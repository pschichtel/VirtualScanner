# VirtualScanner
Scan barcodes from your screen and emit the content as key strokes.

## Action Sequence Syntax

```
seq := actions
actions := action actions | EPSILON
action := key_down | key_up | key_press
key_down := '+' code
key_up := '-' code
key_press := '~' code
code := ('0'..'9')+
```

## Testing

Try generating QR codes using e.g. [this website](https://barcode.tec-it.com). And make screenshots of them or copy the image directly to the clipboard.
