/*
 * Copyright 2017 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.featran.transformers

import java.net.{URLDecoder, URLEncoder}

import com.spotify.featran.FeatureBuilder
import com.twitter.algebird.{Aggregator, Semigroup}

import scala.collection.mutable.{Map => MMap}
import scala.collection.{SortedMap, SortedSet}

object OneHotEncoder {
  /**
   * Transform a collection of categorical features to binary columns, with at most a single
   * one-value.
   *
   * Missing values are transformed to [0.0, 0.0, ...].
   *
   * When using aggregated feature summary from a previous session, unseen labels are ignored.
   */
  def apply(name: String): Transformer[String, SortedSet[String], SortedMap[String, Int]] =
    new OneHotEncoder(name)
}





private class OneHotEncoder(name: String) extends BaseHotEncoder[String](name) {
  override def prepare(a: String): SortedSet[String] = SortedSet(a)
  override def buildFeatures(a: Option[String],
                             c: SortedMap[String, Int],
                             fb: FeatureBuilder[_]): Unit = {
    val kv = for (k <- a; v <- c.get(k)) yield (k, v)
    kv match {
      case Some((k, v)) =>
        fb.skip(v)
        fb.add(name + '_' + k, 1.0)
        fb.skip(math.max(0, c.size - v - 1))
      case None =>
        fb.skip(c.size)
    }
  }
}

private abstract class BaseHotEncoder[A](name: String)
  extends Transformer[A, SortedSet[String], SortedMap[String, Int]](name) {

  def prepare(a: A): SortedSet[String]

  private def present(reduction: SortedSet[String]): SortedMap[String, Int] = {
    val b = SortedMap.newBuilder[String, Int]
    var i = 0
    val it = reduction.iterator
    while (it.hasNext) {
      b += it.next() -> i
      i += 1
    }
    b.result()
  }
  override val aggregator: Aggregator[A, SortedSet[String], SortedMap[String, Int]] = {
    implicit val sortedSetSg = Semigroup.from[SortedSet[String]](_ ++ _)
    Aggregators.from[A](prepare).to(present)
  }
  override def featureDimension(c: SortedMap[String, Int]): Int = c.size
  override def featureNames(c: SortedMap[String, Int]): Seq[String] = {
    c.map(name + '_' + _._1)(scala.collection.breakOut)
  }

  override def encodeAggregator(c: Option[SortedMap[String, Int]]): Option[String] =
    c.map(_.map(e => URLEncoder.encode("label:" + e._1, "UTF-8")).mkString(","))
  override def decodeAggregator(s: Option[String]): Option[SortedMap[String, Int]] = s.map { ks =>
    val a = ks.split(",").filter(_.nonEmpty)
    var i = 0
    val b = SortedMap.newBuilder[String, Int]
    while (i < a.length) {
      b += URLDecoder.decode(a(i), "UTF-8").replaceAll("^label:", "") -> i
      i += 1
    }
    b.result()
  }

}
