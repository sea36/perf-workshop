package com.epickrram.workshop.perf.reporting;

//////////////////////////////////////////////////////////////////////////////////
//   Copyright 2015   Mark Price     mark at epickrram.com                      //
//                                                                              //
//   Licensed under the Apache License, Version 2.0 (the "License");            //
//   you may not use this file except in compliance with the License.           //
//   You may obtain a copy of the License at                                    //
//                                                                              //
//       http://www.apache.org/licenses/LICENSE-2.0                             //
//                                                                              //
//   Unless required by applicable law or agreed to in writing, software        //
//   distributed under the License is distributed on an "AS IS" BASIS,          //
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   //
//   See the License for the specific language governing permissions and        //
//   limitations under the License.                                             //
//////////////////////////////////////////////////////////////////////////////////


import com.beust.jcommander.JCommander;
import com.epickrram.workshop.perf.app.message.JournalEntry;
import com.epickrram.workshop.perf.config.CommandLineArgs;
import org.HdrHistogram.Histogram;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

import static com.epickrram.workshop.perf.reporting.HistogramReporter.HISTOGRAM_REPORTER;
import static com.epickrram.workshop.perf.support.Histograms.HISTOGRAMS;
import static java.nio.channels.FileChannel.open;
import static java.nio.file.StandardOpenOption.READ;

public final class JournalReporter
{
    private final CommandLineArgs commandLineArgs;

    public JournalReporter(final CommandLineArgs commandLineArgs)
    {
        this.commandLineArgs = commandLineArgs;
    }

    public void run() throws IOException
    {
        final FileChannel channel = open(new File(commandLineArgs.getJournalFile()).toPath(), READ);
        final Histogram interMessageLatency = HISTOGRAMS.createHistogram();
        final Histogram messageTransitLatency = HISTOGRAMS.createHistogram();
        final int numberOfEntriesToIgnoreDueToWarmup = 10 * commandLineArgs.getNumberOfRecords();

        final JournalEntry journalEntry = new JournalEntry();
        long previousMessageNanos = 0L;
        int messageCount = 0;

        while(channel.position() < channel.size() - JournalEntry.ENTRY_SIZE)
        {
            journalEntry.readFrom(channel);

            if(!journalEntry.canRead())
            {
                break;
            }

            if(++messageCount > numberOfEntriesToIgnoreDueToWarmup)
            {
                HISTOGRAMS.safeRecord(journalEntry.getDeltaNanos(), messageTransitLatency);
                if (previousMessageNanos != 0L)
                {
                    HISTOGRAMS.safeRecord(journalEntry.getNanoTime() - previousMessageNanos, interMessageLatency);
                }

                previousMessageNanos = journalEntry.getNanoTime();
            }
        }

        HISTOGRAM_REPORTER.writeReport(interMessageLatency,
                "Journaller Inter-Message Latency", System.out);
        HISTOGRAM_REPORTER.writeReport(messageTransitLatency,
                "Journaller Message Transit Latency", System.out);
    }

    public static void main(final String[] args) throws Exception
    {
        final CommandLineArgs commandLineArgs = new CommandLineArgs();
        new JCommander(commandLineArgs).parse(args);

        new JournalReporter(commandLineArgs).run();
    }
}