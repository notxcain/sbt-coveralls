package org.scoverage.coveralls

import scala.io.Source
import io.circe._
import io.circe.parser
import io.circe.generic.auto._

trait CIService {
  def name: String
  def jobId: Option[String]
  def pullRequest: Option[String]
  def currentBranch: Option[String]
}

case object TravisCI extends CIService {
  val name = "travis-ci"
  val jobId: Option[String] = sys.env.get("TRAVIS_JOB_ID")
  val pullRequest: Option[String] = sys.env.get("CI_PULL_REQUEST")
  val currentBranch: Option[String] = sys.env.get("CI_BRANCH")
}

case object TravisPro extends CIService {
  val name = "travis-pro"
  val jobId: Option[String] = sys.env.get("TRAVIS_JOB_ID")
  val pullRequest: Option[String] = sys.env.get("CI_PULL_REQUEST")
  val currentBranch: Option[String] = sys.env.get("CI_BRANCH")
}

case object GitHubActions extends CIService {
  val name = ""
  val jobId: Option[String] = sys.env.get("GITHUB_RUN_ID")

  // https://github.com/coverallsapp/github-action/blob/master/src/run.ts#L31-L40
  val pullRequest: Option[String] = for {
    eventName <- sys.env.get("GITHUB_EVENT_NAME") if eventName.startsWith("pull_request")
    payloadPath <- sys.env.get("GITHUB_EVENT_PATH")
    prNumber <- getPrNumber(payloadPath)
  } yield prNumber

  // https://docs.github.com/en/actions/learn-github-actions/environment-variables
  val currentBranch: Option[String] = pullRequest match {
    case Some(_) => sys.env.get("GITHUB_HEAD_REF")
    case None => sys.env.get("GITHUB_REF_NAME")
  }

  def getPrNumber(payloadPath: String): Option[String] = {
    val lines =
      try {
        Some(Source.fromFile(payloadPath, "utf-8").getLines.mkString)
      } catch {
        case _: Throwable => None
      }

    lines match {
      case Some(ls) => getFromJson(ls, "number")
      case None => None
    }

  }

  def getFromJson(lines: String, element: String): Option[String] = {
    parser.parse(lines) match {
      case Right(json) => {
        json.findAllByKey(element) match {
          case prNumber :: nil => Some(prNumber.toString)
          case _ => None
        }
      }
      case Left(_) => None
    }
  }
}
