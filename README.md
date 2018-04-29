# VirtualScanner

Scan barcodes from your screen and emit the content as key strokes.

## Usage

```java -jar virtual-scanner.jar [mode] [configuration]```

* `[mode]`: The mode (see below) to start in. Can be omitted.
* `[configuration]`: The configuration file to load. Can be omitted and defaults to config.json

### Modes

* `screen`: Scans all screens once for barcodes. This mode will be used if no mode has been specified.
* `clipboard`: Continuously scans the clipboard for images and scans them for barcodes.
* `layout`: A small UI to customize the character-key-mapping.

### Configuration

A JSON file is loaded which configures to tool. The file can be specified or defaults to `config.json`.

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
If the file is not found, defaults will be loaded: [Default config.json on Github](https://github.com/pschichtel/VirtualScanner/blob/master/src/main/resources/config.json).

* `layout`: The layout file to map characters to keys on the keyboard. Relative paths will be relative to the working directory. The file `layout.json` will be loaded internally with defaults, if it does not exist.
* `normalizeLinebreaks`: Whether to normalize the linebreaks to `\n`.
* `prefix`: The key actions to generate *before* the barcode content.
* `suffix`: The key actions to generate *after* the barcode content.
* `delay`: The delay before the tool starts producing key events after detecting the barcode in milliseconds.
* `charset`: The characters to map in the layout file, used by the `layout` mode.
 
### Download

Get the tool from [Github](https://github.com/pschichtel/VirtualScanner/releases/latest)!

## Action Sequence Syntax

* `+10` -> Press key with code 10 down
* `-10` -> Release key with code 10
* `~10` -> Press and release key with code 10

## Testing

Try generating QR codes using e.g. [this website](https://barcode.tec-it.com). And make screenshots of them or copy the image directly to the clipboard.
