import Array._
import java.io._
import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer
import javax.imageio.ImageIO // png
import java.awt.image.BufferedImage // png

// local claases
import rawimagetool._
import localutil.Util._
import refcomp.RefComp._

class AppParams {
  def usage() {
    println("Usage: scala EstimatorMain [options] rawimgfilename")
    println("")
    println("This command reads a simple raw image format that contains a number of glay scala images with 32-bit integer pixel value (its dynamic range is possible smaller). Width and Height are specified via command line. The image framenumber start and stop are also specified via command line.")
    println("")
    println("[Options]")
    println("")
    println("-width int  : width of each image frame")
    println("-height int : height of each image frame")
    println("-psize int  : bytes per pixel: 1 or 4")
    println(s"-fnostart int : start frame number (default: $fnostart)")
    println(s"-fnostop int  : stop frame number (default: $fnostop)")
    println("-gray: dump gray images.")
    println("-png: dump png images.")
    println("-pngregion: dump png images.")
    println("-vsample xpos : dump vertical sample at xpos (default width/2)")
    println("* compressor configs")
    println("-ninpxs int : the number of input pixels")
    println("-nbufpxs int : the number of output buffer pixels")
    println("")
  }

  var filename = ""
  var width = 256
  var height = 256
  var psize = 4
  var fnostart = 0
  var fnostop = 0
  var dump_gray = false
  var dump_png = false
  var dump_pngregion = false
  var dump_vsample = false
  var vsamplexpos = 0
  var bitspx = 9
  // NOTE: assume width and height >= 256. for now, fixed
  val window_width  = 256
  val window_height = 256
  var xoff = 0
  var yoff = 0
  // compressor params. non-adjustable for now
  var ninpxs = 16  // the input size to encoders, which is # of elements
  var nbufpxs = 28

  type AppOpts = Map[String, String]

  // command line option handling
  def nextopts(l: List[String], m: AppOpts) : AppOpts = {
    l match {
      case Nil => m
      case "-h" :: tail => usage() ; sys.exit(1)
      case "-gray" :: istr :: tail => nextopts(tail, m ++ Map("gray" -> istr ))
      case "-png" :: istr :: tail => nextopts(tail, m ++ Map("png" -> istr ))
      case "-pngregion" :: istr :: tail => nextopts(tail, m ++ Map("pngregion" -> istr ))
      case "-vsample" :: istr :: tail => nextopts(tail, m ++ Map("vsample" -> istr ))
      case "-width" :: istr :: tail => nextopts(tail, m ++ Map("width" -> istr ))
      case "-height" :: istr :: tail => nextopts(tail, m ++ Map("height" -> istr ))
      case "-psize" :: istr :: tail => nextopts(tail, m ++ Map("psize" -> istr ))
      case "-fnostart" :: istr :: tail => nextopts(tail, m ++ Map("fnostart" -> istr ))
      case "-fnostop" :: istr :: tail => nextopts(tail, m ++ Map("fnostop" -> istr ))
      case "-xoff" :: istr :: tail => nextopts(tail, m ++ Map("xoff" -> istr ))
      case "-yoff" :: istr :: tail => nextopts(tail, m ++ Map("yoff" -> istr ))
      case "-ninpxs" :: istr :: tail => nextopts(tail, m ++ Map("ninpxs" -> istr ))
      case "-nbufpxs" :: istr :: tail => nextopts(tail, m ++ Map("nbufpxs" -> istr ))
      case str :: Nil => m ++ Map("filename" -> str)
      case unknown => {
        println("Unknown: " + unknown)
        sys.exit(1)
      }
    }
  }

  def getopts(a: Array[String]) {
    val m = nextopts(a.toList, Map())

    filename = m.get("filename") match {
      case Some(v) => v
      case None => println("No filename found"); usage(); sys.exit(1)
    }
    def getIntVal(m: AppOpts, k: String) = m.get(k) match {case Some(v) => v.toInt ; case None => println("No value found: " + k); sys.exit(1)}

    def getBoolVal(m: AppOpts, k: String) = m.get(k) match {case Some(v) => v.toBoolean ; case None => println("No value found: " + k); sys.exit(1)}

    width = getIntVal(m, "width")
    height = getIntVal(m, "height")
    psize = getIntVal(m, "psize")

    // the following lines need to be fixed. not elegant...
    if (m contains "fnostart") fnostart = getIntVal(m, "fnostart")
    if (m contains "fnostop")  fnostop = getIntVal(m, "fnostop")
    if (m contains "xoff")     xoff = getIntVal(m, "xoff")
    if (m contains "yoff")     yoff = getIntVal(m, "yoff")
    if (m contains "ninpxs")   ninpxs = getIntVal(m, "ninpxs")
    if (m contains "nbufpxs")  nbufpxs = getIntVal(m, "nbufpxs")
    if (m contains "gray")     dump_gray = getBoolVal(m, "gray")
    if (m contains "png")      dump_png = getBoolVal(m, "png")
    if (m contains "pngregion")   dump_pngregion = getBoolVal(m, "pngregion")

    m.get("vsample") match {
      case Some(v) => dump_vsample = true; vsamplexpos = v.toInt
      case None => println("vsample needs value")
    }
  }

  def printinfo() = {
    println("[Info]")
    println("dataset: " + filename)
    println("width:   " + width)
    println("height:  " + height)
    println("size:    " + psize)
    println(s"offset:  x=$xoff, y=$yoff")
    println("")
    println("ninpxs:  " + ninpxs)
    println("nbufpxs: " + nbufpxs)
    println("")
  }
}

object EstimatorMain extends App {


  var ap = new AppParams()

  ap.getopts(args)
  ap.printinfo()


  // open a dataset
  val in = new FileInputStream(ap.filename)
  val rawimg = new RawImageTool(ap.width, ap.height)

  val nshifts = (ap.height/ap.ninpxs) * ap.width

  val st = System.nanoTime()

  // need to skip frames
  println(s"Skipping to ${ap.fnostart}")
  for (fno <- 0 until ap.fnostart) {
    rawimg.skipImage(in, ap.psize)
  }

  def optionalprocessing(fno: Int) {
    // write back to file.
    // To display an image, display -size $Wx$H -depth 16  imagefile
    if(ap.dump_gray)  {
      val grayfn = f"fr${fno}.gray"
      println(s"Writing $grayfn")
      rawimg.writeImageShort(grayfn)
    }
    if(ap.dump_png) {
      val pngfn = f"fr${fno}.png"
      println(s"Writing $pngfn")
      rawimg.writePng(pngfn, 1)
    }
    if(ap.dump_pngregion) {
      val pngfn = f"fr${fno}-${ap.window_width}x${ap.window_height}+${ap.xoff}+${ap.yoff}.png"
      println(s"Writing $pngfn")
      rawimg.writePngRegion(pngfn, 1, ap.xoff, ap.yoff,
        ap.window_width, ap.window_height);
    }

    if(ap.dump_vsample) {
      val vs = rawimg.getVerticalLineSample(ap.vsamplexpos, 0, ap.height)
      sys.exit(0)
    }
  }


  val hyd = ap.height - (ap.height % ap.ninpxs) // to simplify, ignore the remaining
  val total_inpxs = ap.width * hyd
  val total_shuffled_inpxs = ap.width * (hyd/ap.ninpxs*ap.bitspx)

  // per-frame stats
  var allzeroratios = new ListBuffer[Float]()
  // var allratios28 = new ListBuffer[Float]()
  // var allratios56 = new ListBuffer[Float]()

  // per frame compression ratio
  var cr_rl = new ListBuffer[Int]()


  def analyzeframe() {
    // per-frame stats
    // enclens is created for each encode scheme
    // rl   : N-input run-length
    // zs   : N-input zero suppression
    // shzs : shuffled N-input zero suppression
    var enclens_rl   = new ListBuffer[Int]()
    var enclens_zs   = new ListBuffer[Int]()
    var enclens_shzs = new ListBuffer[Int]()


    // chunk up to rows whose height is ap.ninpxs
    for (yoff <- 0 until hyd by ap.ninpxs) {
      // each column shift (every cycle in HW)
      for (xoff <- 0 until ap.width) {
        // create a column chunk, which is an input to the compressor
        val indata = List.tabulate(ap.ninpxs)(
          rno =>
          rawimg.getpx(xoff, rno + yoff))

        // only check the number of pixel output
        enclens_rl   += rlEncode(indata).length
        enclens_zs   += zsEncode(indata, ap.bitspx).length
        enclens_shzs += shzsEncode(indata, ap.bitspx).length
      }
    }



    val nrl = enclens_rl reduce(_+_)
    val nzs = enclens_zs reduce(_+_)
    val nshzs = enclens_shzs reduce(_+_)


    val ti = total_inpxs.toFloat
    val tsi = total_shuffled_inpxs.toFloat

    printstats("RL", enclens_rl.toList.map(_.toFloat) )
    printstats("ZS", enclens_zs.toList.map(_.toFloat) )
    printstats("SHZS", enclens_shzs.toList.map(_.toFloat) )

    println(f"RL  : ${total_inpxs}/${nrl} => ${ti/nrl}")
    println(f"ZS  : ${total_inpxs}/${nzs} => ${ti/nzs}")
    println(f"SHZS: ${total_shuffled_inpxs}/${nshzs} => ${tsi/nshzs}")

    val b28nrl = calcNBuffers(enclens_rl.toList, 28) * 28
    val b28nzs = calcNBuffers(enclens_zs.toList, 28) * 28
    val b28nshzs = calcNBuffers(enclens_shzs.toList, 28) * 28
    val b56nrl = calcNBuffers(enclens_rl.toList, 56) * 56
    val b56nzs = calcNBuffers(enclens_zs.toList, 56) * 56
    val b56nshzs = calcNBuffers(enclens_shzs.toList, 56) * 56

    println(f"B28RL  : ${total_inpxs}/${b28nrl} => ${ti/b28nrl}")
    println(f"B28ZS  : ${total_inpxs}/${b28nzs} => ${ti/b28nzs}")
    println(f"B28SHZS: ${total_shuffled_inpxs}/${b28nshzs} => ${tsi/b28nshzs}")
    println(f"B56RL  : ${total_inpxs}/${b56nrl} => ${ti/b56nrl}")
    println(f"B56ZS  : ${total_inpxs}/${b56nzs} => ${ti/b56nzs}")
    println(f"B56SHZS: ${total_shuffled_inpxs}/${b56nshzs} => ${tsi/b56nshzs}")
  }


  for (fno <- ap.fnostart to ap.fnostop) {

    if (ap.psize == 4) rawimg.readImageInt(in)
    else if (ap.psize == 1) rawimg.readImageByte(in)

    optionalprocessing(fno)

    val zeroratio = rawimg.zerocnt.toFloat / (ap.width*ap.height).toFloat
    allzeroratios += zeroratio

    val maxval = rawimg.maxval
    println(f"$fno%04d: zeroratio=$zeroratio%.3f maxval=$maxval")

    analyzeframe()
  }

  val et = System.nanoTime()
  val psec = (et-st)*1e-9
  println(f"Processing Time[Sec] = $psec%.3f")
}
