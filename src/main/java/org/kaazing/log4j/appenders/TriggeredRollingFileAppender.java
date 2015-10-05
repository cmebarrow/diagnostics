/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.log4j.appenders;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.LoggingEvent;

/**
 * This class is a subclass of log4j RollingFileAppender. It stores all logged messages in memory until (and if) there
 * is a match of triggerPattern in the message, then static method printAllMessages is called. The in-memory list of log
 * messages is reset by calling the static initialize method. This class automatically sets the level of the root logger
 * to Level.TRACE, the first time a messages is logged using it. USAGE: configure this class as the ONLY appender on the
 * root Logger (on the <root> element log4j-config.xml) in order to store trace level log messages in memory during a
 * test run without seriously impacting performance or thread concurrency. If a message is logged using this appender
 * that matches one of the configured patterns, then all in memory messages are written out to the configured output
 * file.
 */
public class TriggeredRollingFileAppender extends RollingFileAppender {
    private static final boolean DEBUG = true;

    private static AtomicReference<BlockingQueue<LoggingEvent>> eventsList = new AtomicReference<BlockingQueue<LoggingEvent>>();
    static {
        eventsList.set(new LinkedBlockingQueue<LoggingEvent>());
    }

    private static TriggeredRollingFileAppender lastInstance;
    private int maximumMessages = 1000;
    private int truncateAfter = 200;
    private static AtomicInteger messageCount = new AtomicInteger(0);

    private String triggerPattern = "exception";
    // create a thread for printAllMessages
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    static {
        initialize();
    }

    public static void initialize() {
        eventsList.get().clear();
        lastInstance = null;
        messageCount.set(0);
    }

    public int getmaximumMessages() {
        return maximumMessages;
    }

    public void setmaximumMessages(int max) {
        maximumMessages = max;
    }

    public int gettruncateAfter() {
        return truncateAfter;
    }

    public void settruncateAfter(int numOfChar) {
        truncateAfter = numOfChar;
    }

    public String gettriggerPattern() {
        return triggerPattern;
    }

    public void settriggerPattern(String pattern) {
        triggerPattern = pattern;
    }

    public static void printAllMessages(Queue<LoggingEvent> events) {
        if (lastInstance == null) {
            System.out.println("Unable to print out trace level root logger messages - please "
                    + "configure TriggeredRollingFileAppender on the <root> logger in log4j-config.xml");
        } else {

            debug(String.format("printing last %d log messages", events.size()));
            lastInstance.appendAll(events);
        }
    }

    public TriggeredRollingFileAppender() {
        super();
        lastInstance = this;
        debug("instance " + this.toString() + " created");
    }

    @Override
    protected void subAppend(LoggingEvent event) {
        boolean match = false;
        final String message = (String) event.getMessage();

        // set name of current thread on the event so it's correct when/if we print the message later
        event.getThreadName();
        match = message.toString().matches(triggerPattern);
        if (message != null && message.length() > truncateAfter) {
            truncateMessage(event);
        }
        eventsList.get().add(event);
        // To avoid OOM, limit number of cached messages
        if (match) {
            messageCount.set(0);
            final Queue<LoggingEvent> eventsListOut = eventsList.getAndSet(new LinkedBlockingQueue<LoggingEvent>());
            executorService.submit(new Runnable() {
                public void run() {
                    debug(String.format("instance %s executing printAllMessages asynchronously", this.toString()));
                    printAllMessages(eventsListOut);
                }
            });

        } else if (messageCount.incrementAndGet() > maximumMessages) {
            // remove oldest message
            try {
                eventsList.get().poll(10, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                // no action
            }
        }
    }

    private static String getDateTime() {
        final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        final Date date = new Date();
        return dateFormat.format(date);
    }

    private void truncateMessage(LoggingEvent event) {
        Field field;
        try {
            field = LoggingEvent.class.getDeclaredField("message");
            field.setAccessible(true);
            final String oldMessage = (String) field.get(event);
            final String newMessage = oldMessage.substring(0, truncateAfter);
            field.set(event, newMessage);
        } catch (final Exception e) {
            System.out.println(this + ": caught exception " + e);
        }
    }

    @Override
    public void close() {
        debug("instance " + this + " closed");
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

    private void appendAll(Queue<LoggingEvent> events) {
        try {
            for (final LoggingEvent event : events) {
                super.subAppend(event);
            }
        } finally {
            // Make sure we always free up memory
            events.clear();
            events = null; // remove reference, so it will be GC'ed
        }
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println(getDateTime() + " TriggeredRollingFileAppender: " + message);
        }
    }

}
