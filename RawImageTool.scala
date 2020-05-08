package rawimagetool

import Array._
import java.io._
import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer
import javax.imageio.ImageIO // png
import java.awt.image.BufferedImage // png


// A class for manipulating a simple raw image data set that does not
// include metadata information such as width, height, pixel data
// type, the number of image, etc. This class provides methods that
// loads and stores the raw image and methods that analyzes data
// (maximum value, zero pixel count, etc).
//
class RawImageTool(val width: Int, val height: Int)
{
  // image pixel data
  private var pixels = ofDim[Int](height, width) // index order; y -> x
  // helper function
  def setpx(x: Int, y: Int, v: Int) : Unit = pixels(y)(x) = v
  def getpx(x: Int, y: Int) : Int = pixels(y)(x)

  // statistical info
  var maxval = 0
  var zerocnt = 0

  def resetpixels(v: Int = 0) : Unit = {
    for(y <- 0 until height; x <- 0 until width) setpx(x, y, v)
    maxval = 0
    zerocnt = 0
  }

  def BytesToInt(buf: Array[Byte]) : Int = {
    val ret = ByteBuffer.wrap(buf.reverse).getInt
    /*
    if(buf(3) != 0 ||buf(2) != 0 ||buf(1) != 0) {
      println(f"${buf(3)}:${buf(2)}:${buf(1)}:${buf(0)} => $ret")
    }
     */
    ret
  }

  def ShortToBytes(v: Short): Array[Byte] = {
    val tmp = new Array[Byte](2)

    tmp(0) = v.toByte
    tmp(1) = (v >> 8).toByte

    tmp
  }

  def skipImage(in: FileInputStream, step: Int) {
    in.skip((width*height)*step)
  }

  // read an image with 4-byte integer pixels (4 * w * h bytes) and
  // store it to pixels.  maxval and zerocnt are updated
  def readImageInt(in: FileInputStream) {
    resetpixels()

    val step = 4
    try {
      val buf = new Array[Byte]( (width*height)*step )

      in.read(buf)

      for (y <- 0 until height) {
        for (x <- 0 until width) {
          var idx = y * width + x
          val v = BytesToInt(buf.slice(idx*step, idx*step+4))

          // NOTE: take only positive values
          val clipval=(1<<16)-1
          val v2 = if (v < 0) {0} else {if (v>=clipval) clipval else v}
          // val v2 = v.abs
          if (v2 == 0) zerocnt += 1
          if (v2 > maxval) { maxval = v2 }
          setpx(x, y, v2)
        }
      }
    } catch {
      case e: IOException => println("Failed to read")
    }
  }

  // read an image with 1-byte integer pixels (w * h bytes) and
  // store it to pixels.  maxval and zerocnt are updated
  def readImageByte(in: FileInputStream) {
    resetpixels()

    try {
      val buf = new Array[Byte](width*height)
      in.read(buf)
      for (y <- 0 until height) {
        for (x <- 0 until width) {
          var idx = y * width + x
          val v = buf(idx).toInt
          if (v == 0) zerocnt += 1
          if (v > maxval) { maxval = v }
          setpx(x, y, v)
        }
      }
    } catch {
      case e: IOException => println("Failed to read")
    }
  }


  // implement this later when needed
  // def writeImageInt(fn: String, w: Int, h: Int) : Boolean

  // write the current image into a file for a debug purpose
  def writeImageShort(fn: String) : Boolean = {
    val step = 2
    var buf = new Array[Byte]((width*height) * step)

    for (y <- 0 until height; x <- 0 until width ) {
      val v = getpx(x, y)
      val sval : Short = if (v < 0) 0 else v.toShort
      // val sval : Short = if (v <= 0) 0 else 1
      val tmp = ShortToBytes(sval)
      val idx = y*width + x

      buf(idx*2 + 0) = tmp(0)
      buf(idx*2 + 1) = tmp(1)
    }

    try {
      val out = new FileOutputStream(fn)
      out.write(buf)
      out.close()
    } catch {
      case e: FileNotFoundException => println("Not found:" + fn)
      case e: IOException => println("Failed to write")
    }
    true
  }

  // pixel value higher equal than threshold, assign the max brightness
  def writePng(fn: String, threshold: Int) : Unit = {
    val img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    def graytorgb(a: Int) = (a<<16) | (a<<8) | a

    for (x <- 0 until width) {
      for (y <- 0 until height) {
        val tmp = getpx(x,y)
        val tmp2 = if(tmp>=threshold) 255 else tmp
        img.setRGB(x, y,  graytorgb(tmp2))
      }
    }
    ImageIO.write(img, "png", new File(fn))
  }


  def getVerticalLineSample(x: Int, y1: Int, y2: Int) : List[Int] = {
    List.tabulate(y2-y1) {i => getpx(x, y1 + i) }
  }

  def shuffleVerticalLineSample(data: List[Int], stride: Int) : List[Int] = {
    val a = data.toArray
    List.tabulate(data.length) {i =>  a( ((i%stride)*stride) + (i/stride) )}
  }

  def runlengthIdealEstimate(data: List[Int]) : List[Int] = {
    var curval = 0
    var cnt = 0
    var res = ListBuffer[Int]()

    for (p <- data) {
      if (curval == p) cnt += 1
      else {
        res += curval
        res += cnt
        curval = p
        cnt = 0
      }
    }
    res += curval
    res += cnt

    res.toList
  }

  def zsIdealEstimate(data: List[Int], npxblock: Int) : List[Int] = {
    var res = ListBuffer[Int]()

    for(i <- 0 until data.length by npxblock) {
      res += 0xffff // dummy header
      data.slice(i, i+npxblock).filter(_ > 0).foreach {v => res += v}
    }
    res.toList
  }

  def clipSample(data: List[Int], bitspx: Int) : List[Int] = {
    val maxval = (1<<bitspx) - 1 // move out 
    data.map(v => if(v < maxval) v else maxval)
  }

  def bitshuffleVerticalLineSample(data: List[Int], npxblock: Int, bitspx: Int) : List[Int] = {
    var reslen = (data.length/npxblock) * bitspx
    var res = new Array[Int](reslen)
    var pidx = 0 // index in List


    // convert data to bits array
    // each element hold particular bit in a pixel
    def bits2list(v: Int, b: Int) : List[Int] = { List.tabulate(b) { idx => if (((1<<idx)&v)>0) 1 else 0 } }
    var btmp = ListBuffer[Int]() // a bit wasting...
    for(p <- data) {
      bits2list(p, bitspx).foreach {v => btmp += v}
    }
    val bdata = btmp.toArray
    // println(s"bdata.length=$bdata.length npxblock=$npxblock bitspx=$bitspx")

    // constructing output, filling res
    for (idxbase <- 0 until bdata.length by (npxblock*bitspx)) {
      val residxbase = (idxbase / (npxblock*bitspx)) * bitspx

      // bidx is pixel pos in dst
      for(bidx <- 0 until bitspx) {
        // collect bidx'th bit's value from all pixels in a block
        val tmp = List.tabulate(npxblock)
        {i => bdata(idxbase + i*bitspx + bidx) << i }

        res(residxbase + bidx) = tmp.reduce(_|_)
      }
    }
    res.toList
  }



  def writeData2Text(fn: String, data: List[Int]) {
    try {
      val f = new File(fn)
      val out = new BufferedWriter(new FileWriter(f))
      data.foreach{v => out.write(f"$v\n")}
      out.close()
    } catch {
      case e: FileNotFoundException => println("Not found:" + fn)
      case e: IOException => println("Failed to write")
    }
  }
}
