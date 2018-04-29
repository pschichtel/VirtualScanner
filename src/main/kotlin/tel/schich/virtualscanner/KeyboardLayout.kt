package tel.schich.virtualscanner

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

fun loadLayout(json: ObjectMapper, path: String): Map<Char, List<Action>>? {

    val file = File(path)
    return if (!file.canRead()) null
    else {
        val layoutMap: Map<String, List<Action>>? = read(json, file)
        layoutMap?.map { (k, v) -> Pair(k.first(), v) }?.toMap()
    }

}

fun generateLayoutFile(json: ObjectMapper, path: String, table: Map<Char, List<Action>>) {
    val writer = Files.newBufferedWriter(Paths.get(path), Charsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
    writer.use {
        json.writeValue(it, table)
    }
}
