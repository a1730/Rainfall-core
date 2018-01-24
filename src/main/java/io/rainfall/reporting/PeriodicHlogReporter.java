/*
 * Copyright 2014 Aurélien Broszniowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rainfall.reporting;

import io.rainfall.statistics.StatisticsHolder;
import io.rainfall.statistics.StatisticsPeekHolder;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Ludovic Orban
 */
public class PeriodicHlogReporter<E extends Enum<E>> extends FileReporter<E> {

  private static class Holder {
    Histogram histogram;
    HistogramLogWriter writer;
  }

  private final String basedir;
  private long previousTs;
  private final ConcurrentHashMap<Enum<?>, Holder> previous = new ConcurrentHashMap<Enum<?>, Holder>();

  public PeriodicHlogReporter() {
    this("target/rainfall-histograms");
  }

  public PeriodicHlogReporter(String outputPath) {
    this.basedir = new File(outputPath).getAbsoluteFile().getAbsolutePath();
    this.reportPath = new File(this.basedir);
  }

  @Override
  public void header(final List<String> description) {
    this.previousTs = System.currentTimeMillis();
  }

  @Override
  public void report(final StatisticsPeekHolder<E> statisticsHolder) {
    long now = System.currentTimeMillis();

    Enum<E>[] results = statisticsHolder.getResultsReported();
    for (Enum<E> result : results) {
      Histogram histogram = statisticsHolder.fetchHistogram(result);
      Histogram copy = histogram.copy();
      histogram.setStartTimeStamp(previousTs);
      histogram.setEndTimeStamp(now);

      Holder previous = this.previous.get(result);
      if (previous == null) {
        try {
          previous = new Holder();
          File hlogFile = new File(this.basedir + File.separatorChar + buildHlogFilename(result.name()));
          hlogFile.getParentFile().mkdirs();
          previous.writer = new HistogramLogWriter(new PrintStream(hlogFile));
          previous.writer.setBaseTime(previousTs);
          previous.writer.outputLogFormatVersion();
          previous.writer.outputBaseTime(previous.writer.getBaseTime());
          previous.writer.outputLegend();
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      } else {
        histogram.subtract(previous.histogram);
      }

      previous.histogram = copy;
      this.previous.put(result, previous);
      previous.writer.outputIntervalHistogram(histogram);
      previousTs = now;
    }
  }

  @Override
  public void summarize(final StatisticsHolder<E> statisticsHolder) {
    for (Holder holder : previous.values()) {
      holder.writer.close();
    }
  }

  private String buildHlogFilename(String result) {
    return cleanFilename(result) + ".hlog";
  }

  private final static int[] illegalChars = { 34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
      17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47, '@', '.', '\'', '"', '!', '#', '$',
      '%', '^', '&', '*', '(', ')', '\\' };

  private static String cleanFilename(String filename) {
    Arrays.sort(illegalChars);
    StringBuilder cleanName = new StringBuilder();
    for (int i = 0; i < filename.length(); i++) {
      int c = (int)filename.charAt(i);
      if (Arrays.binarySearch(illegalChars, c) < 0) {
        cleanName.append((char)c);
      }
    }
    return cleanName.toString();
  }

}
