package de.tototec.sbuild.eclipse.plugin

import org.scalatest.FunSuite

class ScalacOutputParserTest extends FunSuite {

  val parser = new ScalacOutputParser()

  test("findEnd") {
    assert(
      parser.findEnd("file", 1, "error", List("firstLine"), List("second line", "  ^")) ===
        (List(ScalacOutputParser.Error("file", 1, Seq("firstLine", "second line", "  ^"))), Nil)
    )
  }

  test("1") {

    val res = parser.parse(
      """|Compiling build script: /home/lefou/work/tototec/aubergine/trunk/aubergine/SBuild.scala...
         |/home/lefou/work/tototec/aubergine/trunk/aubergine/SBuild.scala:3: error: expected class or object definition
         |aimport de.tototec.sbuild._
         |^
         |one error found""".stripMargin
    )

    assert(res === Seq(ScalacOutputParser.Error(
      "/home/lefou/work/tototec/aubergine/trunk/aubergine/SBuild.scala",
      3,
      Seq(
        "expected class or object definition",
        "aimport de.tototec.sbuild._",
        "^")
    )))
  }

  test("2") {

    val res = parser.parse(Seq(
      """Compiling build script: /home/lefou/work/tototec/aubergine/trunk/aubergine/SBuild.scala...""",
      """/home/lefou/work/tototec/aubergine/trunk/aubergine/SBuild.scala:3: error: expected class or object definition""",
      """aimport de.tototec.sbuild._""",
      """^""",
      """one error found"""
    ))

    assert(res ===
      Seq(ScalacOutputParser.Error(
        "/home/lefou/work/tototec/aubergine/trunk/aubergine/SBuild.scala",
        3,
        Seq(
          "expected class or object definition",
          "aimport de.tototec.sbuild._",
          "^")
      )))
  }
}