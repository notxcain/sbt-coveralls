package org.scoverage.coveralls

import java.io.{File, StringWriter, Writer}

import com.fasterxml.jackson.core.JsonFactory
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

class CoverallPayloadWriterTest
    extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers {
  implicit val log = sbt.Logger.Null

  val resourceDir = Utils.mkFileFromPath(Seq(".", "src", "test", "resources"))

  val testGitClient = new GitClient(new File(".")) {
    override def remotes = List("remote")
    override def remoteUrl(remoteName: String) = "remoteUrl"
    override def currentBranch = "branch"
    override def lastCommit() = GitClient.GitRevision(
      "lastCommitId",
      "authorName",
      "authorEmail",
      "committerName",
      "committerEmail",
      "shortMsg"
    )
  }

  def coverallsWriter(
      writer: Writer,
      tokenIn: Option[String],
      service: Option[CIService],
      parallel: Boolean,
  ): (CoverallPayloadWriter, Writer) = {
    val payloadWriter = new CoverallPayloadWriter(
      new File(".").getAbsoluteFile,
      new File("."),
      tokenIn,
      service,
      parallel,
      testGitClient
    ) {
      override def generator(file: File) = {
        val factory = new JsonFactory()
        factory.createGenerator(writer)
      }
    }
    (payloadWriter, writer)
  }

  val expectedGit =
    """"git":{"head":{"id":"lastCommitId","author_name":"authorName","author_email":"authorEmail","committer_name":"committerName","committer_email":"committerEmail","message":"shortMsg"},"branch":"branch","remotes":[{"name":"remote","url":"remoteUrl"}]}"""

  "CoverallPayloadWriter" when {
    "generating coveralls API payload" should {

      "generate a correct starting payload with a job id from a CI service" in {
        val testService: CIService = new CIService {
          override def name = "my-service"
          override def jobId = Some("testServiceJob")
          override def pullRequest = None
          override def currentBranch = None
        }

        val (payloadWriter, writer) = coverallsWriter(
          new StringWriter(),
          Some("testRepoToken"),
          Some(testService),
          false
        )

        payloadWriter.start
        payloadWriter.flush()

        writer.toString should equal(
          """{"repo_token":"testRepoToken","service_name":"my-service","service_job_id":"testServiceJob","parallel":false,""" +
            expectedGit +
            ""","source_files":["""
        )
      }

      "generate a correct starting payload without a CI service" in {
        val (payloadWriter, writer) =
          coverallsWriter(new StringWriter(), Some("testRepoToken"), None, false)

        payloadWriter.start
        payloadWriter.flush()

        writer.toString should equal(
          """{"repo_token":"testRepoToken","parallel":false,""" +
            expectedGit +
            ""","source_files":["""
        )
      }

      "add source files correctly" in {
        val sourceFile = Utils.mkFileFromPath(
          resourceDir,
          Seq(
            "projectA",
            "src",
            "main",
            "scala",
            "bar",
            "foo",
            "TestSourceFile.scala"
          )
        )
        val (payloadWriter, writer) = coverallsWriter(
          new StringWriter(),
          Some("testRepoToken"),
          Some(TravisCI),
          false
        )
        payloadWriter.addSourceFile(
          SourceFileReport(
            sourceFile.getPath(),
            List(Some(1), None, Some(2))
          )
        )
        payloadWriter.flush()

        writer.toString should equal(
          """{"name":"./src/test/resources/projectA/src/main/scala/bar/foo/TestSourceFile.scala","source_digest":"B77361233B09D69968F8C62491A5085F","coverage":[1,null,2]}"""
        )
      }

      "end the file correctly" in {
        val (payloadWriter, writer) = coverallsWriter(
          new StringWriter(),
          Some("testRepoToken"),
          Some(TravisCI),
          false
        )

        payloadWriter.start
        payloadWriter.end()

        writer.toString should endWith("]}")
      }

      "include parallel correctly" in {
        val (payloadWriter, writer) = coverallsWriter(new StringWriter(), Some("testRepoToken"), None, true)

        payloadWriter.start
        payloadWriter.flush()

        writer.toString should equal(
          """{"repo_token":"testRepoToken","parallel":true,""" +
            expectedGit +
            ""","source_files":["""
        )
      }
    }
  }
}
