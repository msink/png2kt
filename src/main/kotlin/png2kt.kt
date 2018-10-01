import kotlinx.cinterop.*
import platform.posix.*
import libpng.*

fun convert_file(name: String) = memScoped {
    print("Reading '$name' ...")

    val header = allocArray<ByteVar>(8)
    val infile = fopen(name, "rb") ?:
        throw Error("File '$name' could not be opened for reading")
    fread(header, 1, 8, infile)
    if (png_sig_cmp(header, 0, 8) != 0) 
        throw Error("File '$name' is not recognized as a PNG file")

    val png_ptr = png_create_read_struct(user_png_ver = PNG_LIBPNG_VER_STRING, error_ptr = null,
        error_fn = staticCFunction { _, message -> throw Error("[libpng] ${message?.toKString()}") },
        warn_fn  = staticCFunction { _, message -> print("\nWarning: ${message?.toKString()}")}) ?:
        throw Error("png_create_read_struct failed")
    val info_ptr = png_create_info_struct(png_ptr) ?:
        throw Error("png_create_info_struct failed")

    png_init_io(png_ptr, infile)
    png_set_sig_bytes(png_ptr, 8)
    png_read_info(png_ptr, info_ptr)

    val width:  Int = png_get_image_width(png_ptr, info_ptr)
    val height: Int = png_get_image_height(png_ptr, info_ptr)
    val stride: Int = png_get_rowbytes(png_ptr, info_ptr).narrow()

    png_set_interlace_handling(png_ptr)
    png_read_update_info(png_ptr, info_ptr)

    val buffer = allocArray<ByteVar>(height * stride)
    val row_pointers = Array<CPointer<ByteVar>?>(height) {
        (buffer.toLong() + (it * stride)).toCPointer()
    }
    png_read_image(png_ptr, row_pointers.toCValues().ptr)

    fclose(infile)
    println(" done")

    png_get_color_type(png_ptr, info_ptr).toInt().let {
        if (it != PNG_COLOR_TYPE_RGBA) throw Error("wrong color_type: $it")
    }

    print("Writing '$name.kt' [$width/$height/$stride] ...")
    val outfile = fopen("$name.kt", "wt") ?:
        throw Error("File '$name.kt' could not be opened for writing")

    fprintf(outfile, "import kotlinx.cinterop.cValuesOf\n")
    fprintf(outfile, "import libui.ktx.draw.ImageData\n\n")
    fprintf(outfile, "val `$name` = ImageData(width=$width, height=$height, stride=$stride, pixels=cValuesOf(")
    for (i in 0 until (height * width)) {
        if (i.rem(8) == 0) fprintf(outfile, "\n    ")
        val r = buffer[i * 4 + 0].toInt() and 0xff
        val g = buffer[i * 4 + 1].toInt() and 0xff
        val b = buffer[i * 4 + 2].toInt() and 0xff
        val a = buffer[i * 4 + 3].toInt() and 0xff
        fprintf(outfile, "0x%02X%02X%02X%02Xu", a, b, g, r)
        if (i < (height * width) - 1) fprintf(outfile, ",")
    }
    fprintf(outfile, "\n))\n")

    fclose(outfile)
    println(" done")
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: png2kt filename.png")
        return
    }

    try {
        args.forEach { convert_file(it) }
    } catch (e: Throwable) {
        println("\nError: ${e.message}")
    }
}
