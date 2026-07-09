#!/usr/bin/env node
/**
 * JavaScript obfuscator CLI wrapper.
 * Usage: node js_obfuscator.js -i <input> -o <output> --scheme easy|diff
 */

const fs = require('fs');
const JavaScriptObfuscator = require('javascript-obfuscator');

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = { input: null, output: null, scheme: 'easy' };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '-i' && i + 1 < args.length) {
      opts.input = args[++i];
    } else if (args[i] === '-o' && i + 1 < args.length) {
      opts.output = args[++i];
    } else if (args[i] === '--scheme' && i + 1 < args.length) {
      opts.scheme = args[++i];
    }
  }
  return opts;
}

function getEasyOptions() {
  return {
    compact: true,
    controlFlowFlattening: false,
    deadCodeInjection: false,
    debugProtection: false,
    disableConsoleOutput: false,
    identifierNamesGenerator: 'hexadecimal',
    numbersToExpressions: true,
    renameGlobals: false,
    selfDefending: false,
    simplify: true,
    splitStrings: true,
    stringArray: true,
    stringArrayEncoding: ['base64'],
    stringArrayThreshold: 0.75,
    unicodeEscapeSequence: false,
    log: false,
  };
}

function getDiffOptions() {
  return {
    compact: true,
    controlFlowFlattening: true,
    controlFlowFlatteningThreshold: 0.75,
    deadCodeInjection: true,
    deadCodeInjectionThreshold: 0.4,
    debugProtection: true,
    debugProtectionInterval: 4000,
    disableConsoleOutput: true,
    identifierNamesGenerator: 'hexadecimal',
    numbersToExpressions: true,
    renameGlobals: false,
    selfDefending: true,
    simplify: true,
    splitStrings: true,
    stringArray: true,
    stringArrayEncoding: ['rc4'],
    stringArrayThreshold: 0.75,
    transformObjectKeys: true,
    unicodeEscapeSequence: false,
    log: false,
  };
}

function main() {
  const opts = parseArgs();

  if (!opts.input || !opts.output) {
    console.error('Usage: node js_obfuscator.js -i <input> -o <output> --scheme easy|diff');
    process.exit(1);
  }

  if (!fs.existsSync(opts.input)) {
    console.error('Input file not found: ' + opts.input);
    process.exit(1);
  }

  const code = fs.readFileSync(opts.input, 'utf-8');
  const obfOptions = opts.scheme === 'diff' ? getDiffOptions() : getEasyOptions();

  try {
    const result = JavaScriptObfuscator.obfuscate(code, obfOptions);
    fs.writeFileSync(opts.output, result.getObfuscatedCode(), 'utf-8');
    console.log('Obfuscation complete: ' + opts.output);
  } catch (err) {
    console.error('Obfuscation failed: ' + err.message);
    process.exit(2);
  }
}

main();
