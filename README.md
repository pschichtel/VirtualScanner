# VirtualScanner
Scan barcodes from your screen and emit the content as key strokes.

## Action Sequence Syntax

```
seq := actions
actions := action actions | EPSILON
action := key nested?
key := '{' SPECIAL_KEY '}' | SIMPLE_KEY
nested := '(' actions ')'
SPECIAL_KEY := 'ENTER', 'F1', 'F2', ...
SIMPLE_KEY := 'a', 'b', ...
```

## Testing

Try generating QR codes using e.g. [this website](https://barcode.tec-it.com). And make screenshots of them or copy the image directly to the clipboard.