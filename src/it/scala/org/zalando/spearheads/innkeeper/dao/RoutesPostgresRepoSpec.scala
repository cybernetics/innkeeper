package org.zalando.spearheads.innkeeper.dao

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}
import org.zalando.spearheads.innkeeper.routes.RoutesRepoHelper
import RoutesRepoHelper.{deleteRoute, insertRoute, routeJson, sampleRoute}
import org.zalando.spearheads.innkeeper.api.PathPatch
import org.zalando.spearheads.innkeeper.routes.RoutesRepoHelper._

class RoutesPostgresRepoSpec extends FunSpec with BeforeAndAfter with Matchers with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  describe("RoutesPostgresRepoSpec") {

    before {
      recreateSchema
    }

    describe("#insert") {
      it("should insert a route") {
        val routeRow = insertRoute()

        routeRow.id.isDefined should be (true)
        routeRow.routeJson should be (routeJson("GET"))
      }
    }

    describe("#selectById") {
      it("should select a route by id") {
        insertRoute()
        val routeRow = routesRepo.selectById(1).futureValue

        routeRow.isDefined should be (true)
        routeRow.get.id should be ('defined)
        routeRow.get.routeJson should be (routeJson("GET"))
      }
    }

    describe("#selectAll") {

      it("should select all routes") {
        val createdAt = LocalDateTime.now()
        val activateAt = createdAt.minusMinutes(5)

        insertRoute("R1", method = "GET", createdAt = createdAt, activateAt = activateAt)
        insertRoute("R2", method = "POST", createdAt = createdAt, activateAt = activateAt)

        val routes: List[RouteRow] = routesRepo.selectAll

        routes should not be 'empty
        routes(0) should be (sampleRoute(
          id = routes(0).id.get,
          pathId = routes(0).pathId,
          name = "R1",
          method = "GET",
          createdAt = createdAt,
          activateAt = activateAt
        ))
        routes(1) should be (sampleRoute(
          id = routes(1).id.get,
          pathId = routes(1).pathId,
          name = "R2",
          method = "POST",
          createdAt = createdAt,
          activateAt = activateAt
        ))
      }

      it("should not select the deleted routes") {
        insertRoute("R1")
        insertRoute("R2")
        val createdAt = LocalDateTime.now()
        insertRoute("R3", createdAt = createdAt)
        insertRoute("R4", createdAt = createdAt)
        deleteRoute(2)

        val routes: List[RouteRow] = routesRepo.selectAll

        routes should not be 'empty
        routes.size should be (3)
        routes.map(_.id.get).toSet should be (Set(1, 3, 4))
      }
    }

    describe("#selectModifiedSince") {
      it("should select the right routes") {
        insertRoute("R1")
        insertRoute("R2")
        val createdAt = LocalDateTime.now()
        insertRoute("R3", createdAt = createdAt)
        insertRoute("R4", createdAt = createdAt)
        deleteRoute(1)

        val routesWithPath: List[(RouteRow, PathRow)] =
          routesRepo.selectModifiedSince(createdAt.minus(1, ChronoUnit.MICROS), LocalDateTime.now())

        routesWithPath.size should be (3)
        routesWithPath.map(_._1.id.get).toSet should be (Set(1, 3, 4))
      }

      it("should select the right activated routes") {
        insertRoute("R1")
        val createdAt = LocalDateTime.now()
        // created in the past, getting active now
        val route2Id = insertRoute("R2", createdAt = createdAt.minusMinutes(1), activateAt = createdAt).id.get
        // will deleted this route
        val route3Id = insertRoute("R3", createdAt = createdAt.minusMinutes(1)).id.get
        insertRoute("R4", createdAt = createdAt, activateAt = createdAt.plusHours(2))
        val route5Id = insertRoute("R5").id.get
        deleteRoute(route3Id)

        val routesWithPath: List[(RouteRow, PathRow)] =
          routesRepo.selectModifiedSince(createdAt.minus(1, ChronoUnit.MICROS), LocalDateTime.now())

        routesWithPath.map(_._1.id.get).toSet should be (Set(route2Id, route3Id, route5Id))
      }

      it("should not select the routes which become disabled") {
        insertRoute("R1")
        val createdAt = LocalDateTime.now()
        // created in the past, getting disabled now
        insertRoute(
          "R2",
          createdAt = createdAt.minusMinutes(10),
          disableAt = Some(createdAt))
        val route3Id = insertRoute("R3").id.get

        val routes: List[(RouteRow, PathRow)] = routesRepo.selectModifiedSince(createdAt.minus(1, ChronoUnit.MICROS), LocalDateTime.now())

        routes.map(_._1.id.get).toSet should be (Set(route3Id))
      }

      it("should select the routes which were updated by a path update") {
        val createdAt = LocalDateTime.of(2015, 10, 10, 10, 10, 10)
        val createdAt1 = createdAt.minusDays(3)
        val createdAt2 = createdAt.minusDays(2)
        val currentTime: LocalDateTime = createdAt.plusDays(5)

        val r1 = insertRoute("R1", createdAt = createdAt1, activateAt = createdAt1)
        insertRoute("R2", createdAt = createdAt2, activateAt = createdAt2)
        insertRoute("R3", createdAt = createdAt, activateAt = createdAt)
        insertRoute("R4", createdAt = createdAt, activateAt = createdAt)

        val updateFuture = pathsRepo.update(r1.id.get, PathPatch(Some(List(1L)), None), createdAt.plusDays(1))
        updateFuture.futureValue

        val result = routesRepo.selectModifiedSince(
          since = createdAt.minusDays(1),
          currentTime = currentTime
        )

        result.size should be (3)
        result.map(_._1.id.get).toSet should be (Set(1, 3, 4))
      }
    }

    describe("#selectByName") {
      it("should select the right routes") {
        val route1Id = insertRoute("R1").id.get
        val route2Id = insertRoute("R2").id.get

        val routes: List[RouteRow] = routesRepo.selectByName("R2")

        routes.size should be (1)
        routes.flatMap(_.id).toSet should be (Set(route2Id))
      }

      it("should not select the deleted routes") {
        insertRoute("R1")
        insertRoute("R2")
        deleteRoute(2)

        val routes: List[RouteRow] = routesRepo.selectByName("R2")

        routes should be('empty)
      }

      it("should select the disabled routes") {
        insertRoute("R2", disableAt = Some(LocalDateTime.now().minusMinutes(3)))
        insertRoute("R1")

        val routes: List[RouteRow] = routesRepo.selectByName("R2")

        routes.size should be (1)
        routes.map(_.id.get).toSet should be (Set(1))
      }
    }

    describe("#selectFiltered") {
      it("should filter by route name") {
        val route1 = insertRoute("R1")
        val route2 = insertRoute("R2")
        val route3 = insertRoute("R3")

        val filters = List(RouteNameFilter(List("R2", "R3")))
        val routes: List[RouteRow] = routesRepo.selectFiltered(filters)

        routes.flatMap(_.id).toSet should be (Set(route2.id, route3.id).flatten)
      }

      it("should filter by team name") {
        val route1 = insertRoute("R1", ownedByTeam = "team-1")
        val route2 = insertRoute("R2", ownedByTeam = "team-2")
        val route3 = insertRoute("R3", ownedByTeam = "team-3")

        val filters = List(TeamFilter(List("team-2", "team-3")))
        val routes: List[RouteRow] = routesRepo.selectFiltered(filters)

        routes.flatMap(_.id).toSet should be (Set(route2.id, route3.id).flatten)
      }

      it("should filter by path uri") {
        val route1 = insertRoute("R1")
        val route2 = insertRoute("R2")
        val route3 = insertRoute("R3")

        val filters = List(PathUriFilter(List("/path-for-R2", "/path-for-R3")))
        val routes: List[RouteRow] = routesRepo.selectFiltered(filters)

        routes.flatMap(_.id).toSet should be (Set(route2.id, route3.id).flatten)
      }

      it("should filter by team and route name") {
        val route1 = insertRoute("R1", ownedByTeam = "team-1")
        val route2 = insertRoute("R2", ownedByTeam = "team-2")
        val route3 = insertRoute("R3", ownedByTeam = "team-3")

        val filters = List(RouteNameFilter(List("R2", "R3")), TeamFilter(List("team-1", "team-2")))
        val routes: List[RouteRow] = routesRepo.selectFiltered(filters)

        routes.flatMap(_.id).toSet should be (Set(route2.id).flatten)
      }

      it("should not select the deleted routes") {
        val route1 = insertRoute("R1")
        val route2 = insertRoute("R2")
        deleteRoute(route1.id.get)

        val routes: List[RouteRow] = routesRepo.selectFiltered()
        routes.flatMap(_.id).toSet should be (Set(route2.id).flatten)
      }

      it("should select the disabled routes") {
        insertRoute("R2", disableAt = Some(LocalDateTime.now().minusMinutes(3)))
        insertRoute("R1")

        val routes: List[RouteRow] = routesRepo.selectFiltered()
        routes.size should be (2)
      }
    }

    describe("#selectDeletedBefore") {
      it("should select nothing if there are no deleted routes") {
        insertRoute("R1")
        insertRoute("R2")

        val dateTime: LocalDateTime = LocalDateTime.now().plusHours(1L)
        val routes: List[RouteRow] = routesRepo.selectDeletedBefore(dateTime)

        routes.size should be (0)
      }

      it("should select only routes that were deleted before the specified date") {
        val insertedRoutes = Seq(insertRoute("R1"), insertRoute("R2"), insertRoute("R3"))

        val now = LocalDateTime.now()
        val lastDeletedAt = insertedRoutes.zipWithIndex
          .map(routeRowWithIndex => {
            val deletedAt = now.plusHours(routeRowWithIndex._2 + 1)
            routeRowWithIndex._1.id.foreach(id => deleteRoute(id, Some(deletedAt)))
            deletedAt
          }).last

        val routes: List[RouteRow] = routesRepo.selectDeletedBefore(lastDeletedAt)

        routes.size should be (2)
        routes.map(_.name) should contain theSameElementsAs Seq("R1", "R2")
      }
    }

    describe("#selectActiveRoutesWithPath") {

      it("should select the right routes") {
        insertRoute("R1")
        // route activated in the future
        insertRoute("R2", activateAt = LocalDateTime.now().plusMinutes(5))
        val createdAt = LocalDateTime.now()
        // disabled route
        insertRoute("R3", createdAt = createdAt, disableAt = Some(createdAt.minusMinutes(1)))
        insertRoute("R4", createdAt = createdAt)
        deleteRoute(4)
        insertRoute("R5", createdAt = createdAt)

        val routesWithPaths = routesRepo.selectActiveRoutesWithPath(LocalDateTime.now())

        routesWithPaths.size should be (2)

        routesWithPaths.map {
          case (routeRow, pathRow) =>
            (routeRow.id.get, pathRow.uri)
        }.toSet should
          be (Set((1, "/path-for-R1"), (5, "/path-for-R5")))
      }
    }

    describe("delete") {
      it("should delete a route by marking as deleted") {
        insertRoute("1", method = "POST")
        insertRoute("2", method = "GET")
        val result = deleteRoute(1)

        result should be (true)

        val routeRow = routesRepo.selectById(1).futureValue

        routeRow should be (defined)
        routeRow.get.id should be (defined)
        routeRow.get.deletedAt should be (defined)
        routeRow.get.deletedBy should be (None)
        routeRow.get.routeJson should be (routeJson("POST"))
      }

      it("should set deleted by if it is provided") {
        val insertRoute1 = insertRoute("1")

        val result = routesRepo.delete(insertRoute1.id.get, Some("user")).futureValue

        result should be (true)

        val routeRow = routesRepo.selectById(1).futureValue

        routeRow should be (defined)
        routeRow.get.deletedBy should be (Some("user"))
      }

      it("should not delete a route that does not exist") {
        routesRepo.delete(1).futureValue should be (false)
      }

      it("should not update the deletedAt date of a route which is already marked as deleted") {
        insertRoute("1")
        insertRoute("2")

        val expectedDeletedAt = LocalDateTime.now()
        routesRepo.delete(1, None, Some(expectedDeletedAt)).futureValue

        // delete the route again
        routesRepo.delete(1, None, Some(expectedDeletedAt.plusHours(1L))).futureValue

        val deletedAt = getDeletedAtForRoute(1)

        deletedAt should be (expectedDeletedAt)
      }

      it("should only delete routes before specified datetime") {
        val insertedRoute1 = insertRoute("1")
        val insertedRoute2 = insertRoute("2")

        deleteRoute(insertedRoute1.id.get, Some(LocalDateTime.now()))

        val dateTime = LocalDateTime.now().plusHours(1L)
        deleteRoute(insertedRoute2.id.get, Some(dateTime))

        val affectedRows = routesRepo.deleteMarkedAsDeletedBefore(dateTime).futureValue

        affectedRows should be (1)

        routesRepo.selectById(insertedRoute1.id.get).futureValue should be (None)
        routesRepo.selectById(insertedRoute2.id.get).futureValue should be (defined)
      }
    }
  }

  private def getDeletedAtForRoute(id: Long) = {
    val routeRow = routesRepo.selectById(id).futureValue
    routeRow.get.deletedAt.get
  }
}
