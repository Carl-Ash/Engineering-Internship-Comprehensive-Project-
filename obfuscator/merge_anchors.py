"""
Simple variable merging: all nodes with the same variable name share the same anchor.
Ignores scopes, definitions, and liveness.
"""

def merge_anchors_by_name(ast_dict):
    """
    Merge anchors for all Name and arg nodes that have the same variable name.
    """
    # Collect all variable names and their anchors
    name_to_anchor = {}  # variable name -> anchor value (first seen)
    
    def collect_nodes(node):
        # Process Name nodes
        if node.get("type") == "Name":
            var_name = node.get("id")
            anchor = node.get("anchor")
            if var_name and anchor and anchor is not None:
                if var_name not in name_to_anchor:
                    name_to_anchor[var_name] = anchor
        # Process arg nodes (function parameters)
        elif node.get("type") == "arg":
            var_name = node.get("arg")
            anchor = node.get("anchor")
            if var_name and anchor and anchor is not None:
                if var_name not in name_to_anchor:
                    name_to_anchor[var_name] = anchor
        elif node.get("type") == "alias":
            var_name = node.get("name")
            anchor = node.get("anchor")
            if var_name and anchor and anchor is not None:
                if var_name not in name_to_anchor:
                    name_to_anchor[var_name] = anchor
        # Recurse
        for child in node.get("children", []):
            collect_nodes(child)
    
    collect_nodes(ast_dict)
    
    # Second pass: replace anchors
    def replace_anchors(node):
        if node.get("type") == "Name":
            var_name = node.get("id")
            if var_name and var_name in name_to_anchor:
                # Only replace if current anchor is not None (hole nodes remain None)
                if node.get("anchor") is not None:
                    node["anchor"] = name_to_anchor[var_name]
        elif node.get("type") == "arg":
            var_name = node.get("arg")
            if var_name and var_name in name_to_anchor:
                if node.get("anchor") is not None:
                    node["anchor"] = name_to_anchor[var_name]
        elif node.get("type") == "alias":
            var_name = node.get("name")
            anchor = node.get("anchor")
            if var_name and anchor and anchor is not None:
                if var_name not in name_to_anchor:
                    name_to_anchor[var_name] = anchor
        for child in node.get("children", []):
            replace_anchors(child)
    
    replace_anchors(ast_dict)