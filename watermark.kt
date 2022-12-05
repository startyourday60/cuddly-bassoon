package watermark
import javax.imageio.ImageIO
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.File
import java.awt.Image
import java.awt.Color
import java.awt.Transparency.TRANSLUCENT
import java.awt.image.BufferedImage
import kotlin.system.exitProcess

// global vars
var useAlphaChannel = false // todo: maybe in singleton. in our way is optional. not need. 
// Occam's razor, Ockham's razor, or Ocham's razor.

// exceptions 
class ImageBadBitsException(val w: String = "image"): Exception("The $w isn't 24 or 32-bit.") {}
class ImageNumberColorComponentException(val w: String = "image"): Exception("The number of $w color components isn't 3.") {}
class ImageFileNotExistsException(val filename: String): Exception("The file $filename doesn't exist.") {}

// enum 
enum class positionMethod {
    SINGLE, GRID
}

// functions 
/*
	if imageName == null then program have to ask about watermark image filename thought stdin
	if imageName is not null, but is string then not will ask input watermark image filename and just use it fur image name
	
	return ImageIO BufferedImage by imageFile in pair wwith filename
*/
fun OpenImage(imageName: String?, isWaterMark: Boolean = false): Pair<String,BufferedImage?> {
    var filename: String
    
    if (imageName == null) {
        if( isWaterMark ) println("Input the watermark image filename:")
        else println("Input the image filename: ")
        filename = readln()
    } else filename = imageName
    
    val imageFile = File(filename)
    if (!imageFile.exists()) {
        //println("The file $filename doesn't exist.")
        //return 0
        return Pair(filename,null)
    }
    
    val myImage: BufferedImage = ImageIO.read(imageFile)
    return Pair(filename, myImage)
}
fun checkImage(img: BufferedImage, isWaterMark: Boolean = false) {
    val map = getInfoAboutImage(img)
    if (map["Number of color components"]!!.toInt() != 3) if (isWaterMark) throw ImageNumberColorComponentException("watermark") else throw ImageNumberColorComponentException()
    if (map["Bits per pixel"]!!.toInt() != 24 && map["Bits per pixel"]!!.toInt() != 32) if (isWaterMark) throw ImageBadBitsException("watermark") else throw ImageBadBitsException()
    if (map["Transparency"] == "TRANSLUCENT" && isWaterMark) {
        println("Do you want to use the watermark's Alpha channel?")
        val answ = readln()
        if (answ.lowercase() == "yes") useAlphaChannel = true
    }
}
// Some but with checks of image. 
fun OpenImage(isWaterMark: Boolean = false): BufferedImage {
    val (filename, myImage) = OpenImage(null,isWaterMark)
    if (myImage == null) throw ImageFileNotExistsException(filename)
    checkImage(myImage, isWaterMark)
    return myImage
}

// get map of params about image
fun getInfoAboutImage(img: BufferedImage): Map<String, String> {
    val ret = mutableMapOf<String, String>()
    ret["Width"] = img.width.toString()
    ret.put("Height", img.height.toString())
    ret.put("Number of components", img.colorModel.numComponents.toString())
    ret.put("Number of color components", img.colorModel.numColorComponents.toString())
    ret.put("Bits per pixel", img.colorModel.pixelSize.toString())
    when (img.transparency) {
        1 -> ret["Transparency"] = "OPAQUE"
        2 -> ret["Transparency"] = "BITMASK"
        3 -> ret["Transparency"] = "TRANSLUCENT"
    }
    return ret
}


// can be inlined maybe.
// just ask percent of transparent of watermark
fun getTransparentPercent(): Int {
    println("Input the watermark transparency percentage (Integer 0-100):")
    val per = readln().toIntOrNull()
    if (per == null) throw Exception("The transparency percentage isn't an integer number.")
    if ( !(per.toInt() in 0..100) ) throw Exception("The transparency percentage is out of range.")
    return per.toInt()
}
// gcan be changed to another
fun getOutputFileName(): String {
    println("Input the output image filename (jpg or png extension):")
    val filename = readln()
    val splitted = filename.split(".")
    if (splitted.last() != "png" && splitted.last() != "jpg") throw Exception("The output file extension isn't \"jpg\" or \"png\".")
    return filename
}


fun main() {
    try {
    
        var transColor: Color? = null
        var XPosition: Int = 0; var YPosition: Int = 0
        var m_posMethod: positionMethod = positionMethod.SINGLE

        val img = OpenImage()
        val waterMark = OpenImage(true)
        if(img.height < waterMark.height || img.width < waterMark.width) {
            println("The watermark's dimensions are larger.")
            exitProcess(1) // fun main(): Int not was found yet
        }
        // get transColor or null.
        if(waterMark.transparency != TRANSLUCENT) {
            println("Do you want to set a transparency color?")
            val answ = readln()
            if (answ.lowercase() == "yes") {
                println("Input a transparency color ([Red] [Green] [Blue]):")
                val colors = readln().split(" ")
                if (colors.size != 3) throw Exception("The transparency color input is invalid.")
                val red = colors[0].toInt(); val green = colors[1].toInt(); val blue = colors[2].toInt()
                if( !(red in 0..255 && green in 0..255 && blue in 0..255) ) throw Exception("The transparency color input is invalid.")
                transColor = Color(red, green, blue)
            }
        }

        val weight = getTransparentPercent()
        // choosing of method (single, grid)
        run {
            println("Choose the position method (single, grid):")
            m_posMethod = when (readln().lowercase()) {
                "single" -> positionMethod.SINGLE
                "grid" -> positionMethod.GRID
                else -> throw Exception("The position method input is invalid.")
            }
            when(m_posMethod) {
                positionMethod.GRID -> {XPosition = 0; YPosition = 0}
                positionMethod.SINGLE -> {
                    val DiffX = img.width - waterMark.width
                    val DiffY = img.height - waterMark.height
                    println("Input the watermark position ([x 0-$DiffX] [y 0-$DiffY]):")
                    val sPositions = readln().split(" ")
                    if (sPositions.size != 2 || sPositions[0].toIntOrNull() == null || sPositions[1].toIntOrNull() == null)
                        throw Exception("The position input is invalid.")
                    XPosition = sPositions[0].toInt(); YPosition = sPositions[1].toInt()
                    if ( XPosition !in 0..DiffX || YPosition !in 0..DiffY) throw Exception("The position input is out of range.")
                }
            }

        }


        val outputName = getOutputFileName()

        //
        // draw image
        run {
            val output = BufferedImage(img.width, img.height, TYPE_INT_RGB)
            val height = img.height
            val width = img.width
            try {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        //var skip = false
                        val i = Color(img.getRGB(x, y))
                        val w =
                                if ( m_posMethod == positionMethod.SINGLE &&
                                        ((x in XPosition until XPosition+waterMark.width) && (y in YPosition until YPosition+waterMark.height)))
                                    Color(waterMark.getRGB(x - XPosition, y - YPosition),useAlphaChannel)
                                else if (m_posMethod != positionMethod.SINGLE) Color(waterMark.getRGB(x % waterMark.width, y % waterMark.height), useAlphaChannel)
                                else
                                    Color(255,255,255, 255)



                        if (useAlphaChannel && (w.alpha == 0) || (w.alpha == 255 && w.red == 255 && w.green == 255 && w.blue == 255)) {
                            output.setRGB(x, y, Color(img.getRGB(x, y)).rgb)
                        } else {
                            val color = Color(
                                    (weight * w.red + (100 - weight) * i.red) / 100,
                                    (weight * w.green + (100 - weight) * i.green) / 100,
                                    (weight * w.blue + (100 - weight) * i.blue) / 100
                            )
                            if((transColor != null && w == transColor))
                            {
                                output.setRGB(x,y, i.rgb)
                            } else {
                                output.setRGB(x,y, color.rgb)
                            }
                        } // else
                    }
                }
            } catch(exc: Exception) {
                println("ImageOutOfRange (BUG?)")
            }
            ImageIO.write(output, outputName.split(".").last(), File(outputName))
            println("The watermarked image $outputName has been created.")
        }


    } catch(exc: Exception) {
        val msgError = exc.toString().split(": ")[1]
        //System.err.println() // exist for stderr?
        println(msgError)
    }
}
