package org.zalando.spearheads.innkeeper.routes

import akka.http.scaladsl.server.Directives.{get, parameterMultiMap}
import akka.http.scaladsl.server.Route
import com.google.inject.Inject
import org.slf4j.LoggerFactory
import org.zalando.spearheads.innkeeper.RouteDirectives.chunkedResponseOf
import org.zalando.spearheads.innkeeper.api.{JsonService, RouteOut}
import org.zalando.spearheads.innkeeper.metrics.RouteMetrics
import org.zalando.spearheads.innkeeper.oauth.OAuthDirectives.hasOneOfTheScopes
import org.zalando.spearheads.innkeeper.oauth.{AuthenticatedUser, Scopes}
import org.zalando.spearheads.innkeeper.services.RoutesService
import org.zalando.spearheads.innkeeper.api.JsonProtocols._
import org.zalando.spearheads.innkeeper.dao.{PathUriFilter, RouteNameFilter, TeamFilter, QueryFilter}

/**
 * @author dpersa
 */
class GetRoutes @Inject() (
    routesService: RoutesService,
    jsonService: JsonService,
    metrics: RouteMetrics,
    scopes: Scopes) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def apply(authenticatedUser: AuthenticatedUser): Route = {
    get {
      val reqDesc = "get /routes"
      hasOneOfTheScopes(authenticatedUser, reqDesc, scopes.READ, scopes.ADMIN) {
        metrics.getRoutes.time {
          logger.info(s"try to $reqDesc")

          parameterMultiMap { parameterMultiMap =>
            val filters = parameterMultiMap.flatMap {
              case ("name", routeNames) => Some[QueryFilter](
                RouteNameFilter(routeNames)
              )
              case ("owned_by_team", teams) => Some[QueryFilter](
                TeamFilter(teams)
              )
              case ("uri", pathUris) => Some[QueryFilter](
                PathUriFilter(pathUris)
              )
              case _ => None
            }

            chunkedResponseOf[RouteOut](jsonService) {
              routesService.findFiltered(filters.toList)
            }
          }
        }
      }
    }
  }
}
