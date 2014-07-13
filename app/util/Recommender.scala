package util

import model.AmazonRating
import org.apache.spark.SparkContext
import org.apache.spark.mllib.recommendation.{ALS, Rating}

import scala.util.Random

class Recommender(@transient sc: SparkContext, ratingFile: String) extends Serializable {
  val NumRecommendations = 10
  val MinRecommendationsPerUser = 10
  val MaxRecommendationsPerUser = 20
  val MyUsername = "myself"

  @transient val random = new Random() with Serializable
  // first create an RDD out of the rating file

  // parse ratings.csv and only keep users that have rated more than MinRecommendationsPerUser products
  val trainingRatings = sc.textFile(ratingFile).map {
    line =>
      val Array(userId, productId, scoreStr) = line.split(",")
      AmazonRating(userId, productId, scoreStr.toDouble)
  }.groupBy(_.userId).filter(r => r._2.size > MinRecommendationsPerUser && r._2.size < MaxRecommendationsPerUser).flatMap(_._2).cache()

  println(s"Parsed $ratingFile. Kept ${trainingRatings.count()} rows.")

  // create user and item dictionaries
  val userDict = new Dictionary(MyUsername +: trainingRatings.map(_.userId).distinct.collect)
  val productDict = new Dictionary(trainingRatings.map(_.productId).distinct.collect)

  private def toSparkRating(amazonRating: AmazonRating) = {
    Rating(userDict.getIndex(amazonRating.userId),
      productDict.getIndex(amazonRating.productId),
      amazonRating.rating)
  }

  private def toAmazonRating(rating: Rating) = {
    AmazonRating(userDict.getWord(rating.user),
      productDict.getWord(rating.product),
      rating.rating
    )
  }

  // convert to Spark Ratings using the dictionaries
  val sparkRatings = trainingRatings.map(toSparkRating)

  def getRandomProductId = productDict.getWord(random.nextInt(productDict.size))

  def predict(ratings: Seq[AmazonRating]) = {
    // train model
    val myRatings = ratings.map(toSparkRating)
    val myRatingRDD = sc.parallelize(myRatings)
    val model = ALS.train(sparkRatings ++ myRatingRDD, 10, 20)

    val myProducts = myRatings.map(_.product).toSet
    val candidates = sc.parallelize((0 until productDict.size).filterNot(myProducts.contains))

    // get all products not in my history
    val myUserId = userDict.getIndex(MyUsername)
    println("=======> " + myUserId)
    val recommendations = model.predict(candidates.map((myUserId, _))).collect()
    recommendations.sortBy(-_.rating).take(NumRecommendations).map(toAmazonRating)
  }
}