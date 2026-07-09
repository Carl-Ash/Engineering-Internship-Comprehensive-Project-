"""
Semantic similarity using local GraphCodeBERT.
"""

import os
import torch
import numpy as np
import re
from typing import Dict

MODEL_PATH = "../GraphCodeBERT"

_tokenizer = None
_model = None
_use_graphcodebert = True

def get_model():
    global _tokenizer, _model, _use_graphcodebert
    if _tokenizer is None and _use_graphcodebert:
        try:
            # 检查路径是否存在且包含必要文件
            if not os.path.exists(MODEL_PATH):
                raise FileNotFoundError(f"Model path not found: {os.path.abspath(MODEL_PATH)}")
            # 检查关键文件（至少需要 config.json 和 pytorch_model.bin）
            required = ['config.json', 'pytorch_model.bin']
            missing = [f for f in required if not os.path.exists(os.path.join(MODEL_PATH, f))]
            if missing:
                raise FileNotFoundError(f"Missing required files: {missing}")

            print(f"Loading GraphCodeBERT from {os.path.abspath(MODEL_PATH)}...")
            from transformers import RobertaTokenizer, RobertaModel
            # GraphCodeBERT 使用与 RoBERTa 相同的 tokenizer 和 model 类
            _tokenizer = RobertaTokenizer.from_pretrained(MODEL_PATH, local_files_only=True)
            _model = RobertaModel.from_pretrained(MODEL_PATH, local_files_only=True)
            _model.eval()
            if torch.cuda.is_available():
                _model.cuda()
                print("Using GPU")
            else:
                print("Using CPU")
            _use_graphcodebert = True
        except Exception as e:
            print(f"Failed to load GraphCodeBERT: {e}")
            print("Falling back to TF-IDF similarity.")
            _use_graphcodebert = False
            _tokenizer = None
            _model = None
    return _tokenizer, _model

def encode_text_graphcodebert(text: str):
    """Encode a single text to a fixed-length vector (mean pooling)."""
    tokenizer, model = get_model()
    if not _use_graphcodebert:
        return None
    inputs = tokenizer(text, return_tensors="pt", truncation=True, max_length=512, padding=True)
    if torch.cuda.is_available():
        inputs = {k: v.cuda() for k, v in inputs.items()}
    with torch.no_grad():
        outputs = model(**inputs)
    attention_mask = inputs["attention_mask"]
    token_embeddings = outputs.last_hidden_state
    input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, dim=1)
    sum_mask = torch.clamp(input_mask_expanded.sum(dim=1), min=1e-9)
    mean_pooled = sum_embeddings / sum_mask
    return mean_pooled.cpu().numpy().flatten()

def similarity_tfidf(text1: str, text2: str) -> float:
    """Fallback TF-IDF similarity."""
    try:
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.metrics.pairwise import cosine_similarity
        vect = TfidfVectorizer(stop_words=None, token_pattern=r'(?u)\b\w+\b')
        tfidf = vect.fit_transform([text1, text2])
        sim = cosine_similarity(tfidf[0:1], tfidf[1:2])[0][0]
        return sim if not np.isnan(sim) else 0.0
    except ImportError:
        # Ultra-fallback: Jaccard
        def tokenize(t):
            return set(re.findall(r'\b\w+\b', t.lower()))
        s1, s2 = tokenize(text1), tokenize(text2)
        if not s1 or not s2:
            return 0.0
        inter = len(s1 & s2)
        union = len(s1 | s2)
        return inter / union if union > 0 else 0.0

def similarity(text1: str, text2: str) -> float:
    """Main similarity function: GraphCodeBERT if available, else TF-IDF."""
    if not text1 or not text2:
        return 0.0
    get_model()  # try to load GraphCodeBERT
    if _use_graphcodebert:
        vec1 = encode_text_graphcodebert(text1)
        if vec1 is None:
            return similarity_tfidf(text1, text2)
        vec2 = encode_text_graphcodebert(text2)
        if vec2 is None:
            return similarity_tfidf(text1, text2)
        norm1 = np.linalg.norm(vec1)
        norm2 = np.linalg.norm(vec2)
        if norm1 == 0 or norm2 == 0:
            return 0.0
        return float(np.dot(vec1, vec2) / (norm1 * norm2))
    else:
        return similarity_tfidf(text1, text2)
    
def extract_node_content(node: Dict) -> str:
    node_type = node.get("type", "Unknown")
    
    # Comment nodes
    if node_type == "Comment":
        comment_type = node.get("comment_type", "")
        value = node.get("value", "")
        short_value = value.replace('\n', ' ').strip()[:60]
        return f"({comment_type}) {short_value}" if comment_type else short_value
    
    # Function / Class definitions
    if node_type in ("FunctionDef", "AsyncFunctionDef"):
        return f"def {node.get('name', '<anonymous>')}"
    if node_type == "ClassDef":
        return f"class {node.get('name', '<anonymous>')}"
    
    # Control flow statements
    if node_type == "For":
        target = node.get("target")
        iter_val = node.get("iter")
        text = "for loop"
        if target and target.get("type") == "Name":
            text = f"for {target.get('id')}"
        if iter_val:
            text += f" in {extract_node_content(iter_val)}"
        return text
    if node_type == "While":
        test = node.get("test")
        text = "while loop"
        if test:
            text += f" with condition {extract_node_content(test)}"
        return text
    if node_type == "If":
        test = node.get("test")
        text = "if statement"
        if test:
            text += f" condition {extract_node_content(test)}"
        return text
    if node_type == "Try":
        return "try block"
    if node_type == "With":
        return "with statement"
    
    # Other statements
    if node_type == "Return":
        value_node = node.get("value")
        if value_node:
            return f"return {extract_node_content(value_node)}"
        return "return"
    if node_type == "Assign":
        targets = node.get("targets", [])
        if targets and isinstance(targets[0], dict) and targets[0].get("type") == "Name":
            return f"assign to {targets[0].get('id')}"
        return "assignment"
    if node_type == "AugAssign":
        target = node.get("target")
        if target and target.get("type") == "Name":
            return f"augment {target.get('id')}"
        return "augmented assignment"
    if node_type == "Expr":
        value_node = node.get("value")
        if value_node:
            return extract_node_content(value_node)
        return "expression"
    
    # Expressions
    if node_type == "Name":
        return node.get("id", "")
    if node_type == "Constant":
        val = node.get("value")
        if isinstance(val, str):
            return f"'{val[:50]}'"
        return str(val)
    if node_type == "arg":
        return node.get("arg", "")
    if node_type == "Attribute":
        return node.get("attr", "")
    if node_type == "Call":
        func = node.get("func")
        if func:
            return f"call to {extract_node_content(func)}"
        return "function call"
    if node_type == "BinOp":
        left = node.get("left")
        right = node.get("right")
        op = node.get("op", {}).get("type", "op")
        if left and right:
            return f"{extract_node_content(left)} {op.lower()} {extract_node_content(right)}"
        return "binary operation"
    if node_type == "Compare":
        return "comparison"
    
    # Collections
    if node_type == "List":
        return "list"
    if node_type == "Dict":
        return "dict"
    if node_type == "Tuple":
        return "tuple"
    if node_type == "Set":
        return "set"
        
    if node_type is None:
        return ""
    
    # Default: return the node type in lowercase as a fallback
    return node_type.lower()