# VirtualScanner

Scan barcodes from your screen and emit the content as key strokes.

## Usage

```java -jar virtual-scanner.jar <mode>```

### Modes

* `screen`: Scans all screens once for barcodes
* `clipboard`: Continuously scans the clipboard for images and scans them for barcodes
* `layout`: A small UI to customize the character-key-mapping

### Configuration

A file called config.json is loaded from the working directory which configures to tool.

```yaml
{
    "layout": "layout.json",
    "normalizeLinebreaks": true,
    "prefix": [],
    "suffix": [],
    "delay": 1000,
    "charset": "abc"
}
```
(Default config: [Github](https://github.com/pschichtel/VirtualScanner/blob/master/src/main/resources/config.json))

* `layout`: the layout file to load
* `normalizeLinebreaks`: Whether to normalize the linebreaks to `\n`
* `prefix`: The key actions to generate *before* the barcode content
* `suffix`: The key actions to generate *after* the barcode content
* `delay`: The delay before the tool starts producing key events after detecting the barcode in milliseconds
* `charset`: The characters to map in the layout file, used by the `layout`.
 
### Download

Get the tool from [Github](https://github.com/pschichtel/VirtualScanner/releases/latest)!

## Action Sequence Syntax

* `+10` -> Press key with code 10 down
* `-10` -> Release key with code 10
* `~10` -> Press and release key with code 10

## Testing

Try generating QR codes using e.g. [this website](https://barcode.tec-it.com). And make screenshots of them or copy the image directly to the clipboard.
