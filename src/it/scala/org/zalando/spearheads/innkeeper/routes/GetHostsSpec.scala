package org.zalando.spearheads.innkeeper.routes

import akka.http.scaladsl.model.StatusCodes
import org.scalatest.{FunSpec, Matchers}
import org.zalando.spearheads.innkeeper.api.{Error, Host}
import org.zalando.spearheads.innkeeper.routes.AcceptanceSpecToken._
import org.zalando.spearheads.innkeeper.routes.AcceptanceSpecsHelper._
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.zalando.spearheads.innkeeper.api.JsonProtocols._

class GetHostsSpec extends FunSpec with Matchers {

  describe("get /hosts") {

    describe("success") {
      it("should return hosts from config file") {
        val response = getSlashHosts(READ_TOKEN)
        response.status should be(StatusCodes.OK)

        val entity = entityString(response)

        val hosts = entity.parseJson.convertTo[Seq[Host]]
        hosts.size should be(2)
        hosts should contain theSameElementsAs Seq(Host(1L, "service.com"), Host(2L, "m.service.com"))
      }
    }

    describe("failure") {
      it("should return 401 if token was not provided") {
        val response = getSlashHosts()

        response.status should be(StatusCodes.Unauthorized)
      }

      it("should return 403 if wrong token was provided") {
        val response = getSlashHosts(WRITE_TOKEN)

        response.status should be (StatusCodes.Forbidden)
        entityString(response).parseJson.convertTo[Error].errorType should be("AUTH1")
      }
    }
  }
}
