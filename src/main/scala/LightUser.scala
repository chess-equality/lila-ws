package lila.ws

import com.github.blemale.scaffeine.{ AsyncLoadingCache, Scaffeine }
import javax.inject._
import reactivemongo.api.bson._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
final class LightUserApi @Inject() (mongo: Mongo)(implicit executionContext: ExecutionContext) {

  type TitleName = String

  def get(id: User.ID): Future[TitleName] = cache get id

  private val cache: AsyncLoadingCache[User.ID, TitleName] =
    Scaffeine()
      .expireAfterWrite(15.minutes)
      .buildAsyncFuture(fetch)

  private def fetch(id: User.ID): Future[TitleName] = mongo.user {
    _.find(
      BSONDocument("_id" -> id),
      Some(BSONDocument("username" -> true, "title" -> true))
    ).one[BSONDocument] map { docOpt =>
        {
          for {
            doc <- docOpt
            name <- doc.getAsOpt[String]("username")
          } yield doc.getAsOpt[String]("title").fold(name)(_ + " " + name)
        } getOrElse id
      }
  }
}
