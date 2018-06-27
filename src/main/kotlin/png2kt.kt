import kotlinx.cinterop.*
import platform.posix.*
import libpng.*

var width: Int = 0
var height: Int = 0
var stride: Int = 0
var buffer: png_bytep? = null

@Suppress("UNUSED_PARAMETER")
fun png_warning(png_ptr: png_structp?, message: png_const_charp?) {
   println("libpng warning: ${message?.toKString()}")
}

@Suppress("UNUSED_PARAMETER")
fun png_error(png_ptr: png_structp?, message: png_const_charp?) {
   throw Error("libpng error: ${message?.toKString()}")
}

fun read_png_file(name: String) = memScoped {
    print("Reading '$name' ...")

    /* open file and test for it being a png */
    val header = allocArray<ByteVar>(8)
    val fp = fopen(name, "rb") ?:
        throw Error("File '$name' could not be opened for reading")
    fread(header, 1, 8, fp)
    if (png_sig_cmp(header, 0, 8) != 0) 
        throw Error("File '$name' is not recognized as a PNG file")

    /* initialize stuff */
    val png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, null,
            staticCFunction(::png_error), staticCFunction(::png_warning)) ?:
        throw Error("png_create_read_struct failed")
    val info_ptr = png_create_info_struct(png_ptr) ?:
        throw Error("png_create_info_struct failed")

    png_init_io(png_ptr, fp)
    png_set_sig_bytes(png_ptr, 8)

    png_read_info(png_ptr, info_ptr)

    width = png_get_image_width(png_ptr, info_ptr)
    height = png_get_image_height(png_ptr, info_ptr)
    stride = png_get_rowbytes(png_ptr, info_ptr).narrow()

    png_set_interlace_handling(png_ptr)
    png_read_update_info(png_ptr, info_ptr)

    /* read file */
    buffer = nativeHeap.allocArray<png_byteVar>(height * stride)
    val row_pointers = allocArray<png_bytepVar>(height)
    for (y in 0 until height)
        row_pointers[y] = (buffer.toLong() + (y * stride)).toCPointer()
    png_read_image(png_ptr, row_pointers)
    fclose(fp)

    println(" done")
}

fun write_kt_file(name: String) {
    if (width <= 0 || height <= 0 || stride <= 0 || (buffer == null))
        throw Error("wrong size: $width/$height/$stride/$buffer")
    print("Writing '$name' [$width/$height/$stride] ...")

    for (i in 0 until (height * width)) {
        val r = buffer!![i * 4 + 0].toInt() and 0xff
        val g = buffer!![i * 4 + 1].toInt() and 0xff
        val b = buffer!![i * 4 + 2].toInt() and 0xff
        val a = buffer!![i * 4 + 3].toInt() and 0xff
        printf("%02X%02X%02X%02X, ", a, r, g, b)
        if (i.rem(8) == 0) printf("\n")
    }

    nativeHeap.free(buffer!!)
    println(" done")
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: png2kt filename.png")
        return
    }

    try {
        args.forEach {
            read_png_file(it)
            write_kt_file("$it.kt")
        }
    } catch (e: Throwable) {
        println("\nError: ${e.message}")
    }
}
