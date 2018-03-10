# VirtualScanner
Scan barcodes from your screen and emit the content as key strokes.

## Action Sequence Syntax

```
seq := actions
actions := action actions | EPSILON
action := key nested?
key := '{' SPECIAL_KEY '}' | SIMPLE_KEY
nexted := '(' seq ')'
SPECIAL_KEY := 'ENTER', 'F1', 'F2', ...
SIMPLE_KEY := 'a', 'b', ...
```
