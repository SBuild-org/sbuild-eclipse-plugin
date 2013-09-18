package de.tototec.sbuild.eclipse.plugin

class ScalacOutputParser {

  def parse(output: String): Seq[ScalacOutputParser.Issue] = parse(output.split("[\n]").toSeq)

  private[plugin] val FirstLinePattern = """^(.*):([0-9]+): (error|warning): (.*)$""".r
  private[plugin] val LastLinePattern = """^ *[\^] *$""".r

  private[plugin] def findNextIssue(output: List[String]): (List[ScalacOutputParser.Issue], List[String]) = output match {
    case Nil => (Nil, Nil)
    case FirstLinePattern(file, nr, level, msg) :: rest => findEnd(file, nr.toInt, level, msg :: Nil, rest)
    case head :: tail => findNextIssue(tail)
  }

  private[plugin] def findEnd(file: String, line: Int, level: String, fullMsg: List[String], output: List[String]): (List[ScalacOutputParser.Issue], List[String]) = {
    output match {
      case Nil => (Nil, Nil)
      case LastLinePattern() :: rest =>
//      case head :: rest if head.trim == "^" =>
        val msg = (output.head :: fullMsg).reverse
        level match {
          case "error" => (ScalacOutputParser.Error(file, line, msg) :: Nil, rest)
          case "warning" => (ScalacOutputParser.Warning(file, line, msg) :: Nil, rest)
          case _ => (Nil, rest)
        }
      case head :: rest => findEnd(file, line, level, head :: fullMsg, rest)
    }
  }

  def parse(output: Seq[String]): Seq[ScalacOutputParser.Issue] = {

    var toParse = output.toList
    var results: List[List[ScalacOutputParser.Issue]] = Nil
    while (toParse != Nil) {
      val (res, rest) = findNextIssue(toParse)
      results ::= res
      toParse = rest
    }

    results.reverse.flatten
  }

}

object ScalacOutputParser {

  sealed trait Issue {
    def file: String
    def line: Int
    def issue: Seq[String]
  }
  case class Error(file: String, line: Int, issue: Seq[String]) extends Issue
  case class Warning(file: String, line: Int, issue: Seq[String]) extends Issue

}