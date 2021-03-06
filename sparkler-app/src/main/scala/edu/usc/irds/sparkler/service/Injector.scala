/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.usc.irds.sparkler.service

import java.io.File
import java.util
import edu.usc.irds.sparkler.{SparklerConfiguration, Constants}
import edu.usc.irds.sparkler.base.{CliTool, Loggable}
import edu.usc.irds.sparkler.model.{Resource, ResourceStatus, SparklerJob}
import edu.usc.irds.sparkler.util.JobUtil
import org.kohsuke.args4j.Option
import org.kohsuke.args4j.spi.StringArrayOptionHandler

import scala.collection.JavaConversions._
import scala.io.Source

/**
  *
  * @since 5/28/16
  */
class Injector extends CliTool {
  import Injector.LOG

  // Load Sparkler Configuration
  val conf: SparklerConfiguration = Constants.defaults.newDefaultConfig()

  @Option(name = "-sf", aliases = Array("--seed-file"), forbids = Array("-su"),
    usage = "path to seed file")
  var seedFile: File = _

  @Option(name = "-su", aliases = Array("--seed-url"), usage = "Seed Url(s)",
    forbids = Array("-sf"), handler = classOf[StringArrayOptionHandler])
  var seedUrls: Array[String] = _

  @Option(name = "-id", aliases = Array("--job-id"),
    usage = "Id of an existing Job to which the urls are to be injected. No argument will create a new job")
  var jobId: String = ""

  override def run(): Unit = {

    if (jobId.isEmpty) {
      jobId = JobUtil.newJobId()
    }
    val job = new SparklerJob(jobId, conf)

    val urls: util.Collection[String] =
      if (seedFile != null) {
        if (seedFile.isFile) {
          Source.fromFile(seedFile).getLines().toList
        } else { // FIXME: scan directory
          throw new RuntimeException("Not implemented yet")
        }
      } else {
        seedUrls.toList
      }

    // TODO: Add URL normalizer and filters before injecting the seeds

    LOG.info("Injecting {} seeds", urls.size())
    val seeds: util.Collection[Resource] =
      urls.map(_.trim)
        .filter(x => !x.isEmpty)
        .map(x => new Resource(x, 0, job, ResourceStatus.NEW))
    val solrClient = job.newCrawlDbSolrClient()
    solrClient.addResources(seeds.iterator())
    solrClient.commitCrawlDb()
    solrClient.close()
  }

  override def parseArgs(args: Array[String]): Unit ={
    super.parseArgs(args)
    if (seedFile == null && seedUrls == null) {
      cliParser.printUsage(Console.out)
      throw new RuntimeException("either -sf or -su should be specified")
    }
  }
}

object Injector extends Loggable {

  def main(args: Array[String]): Unit ={
    val injector = new Injector()
    injector.run(args)
    println(s">>jobId = ${injector.jobId}")
  }
}
