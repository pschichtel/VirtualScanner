# VirtualScanner

Scan barcodes from your screen and emit the content as key strokes.

## Usage

```java -jar virtual-scanner.jar <mode>```

### Modes

* `screen`: Scans all screens once for barcodes
* `clipboard`: Continuously scans the clipboard for images and scans them for barcodes
* `layout`: A small UI to customize the character-key-mapping

## Action Sequence Syntax

* `+10` -> Press key with code 10 down
* `-10` -> Release key with code 10
* `~10` -> Press and release key with code 10

## Testing

Try generating QR codes using e.g. [this website](https://barcode.tec-it.com). And make screenshots of them or copy the image directly to the clipboard.
