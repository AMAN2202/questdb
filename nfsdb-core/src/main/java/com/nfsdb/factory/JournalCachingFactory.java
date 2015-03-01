/*
 * Copyright (c) 2014. Vlad Ilyushchenko
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

package com.nfsdb.factory;

import com.nfsdb.Journal;
import com.nfsdb.JournalBulkReader;
import com.nfsdb.JournalKey;
import com.nfsdb.TimerCache;
import com.nfsdb.exceptions.JournalException;
import com.nfsdb.factory.configuration.JournalConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalCachingFactory extends AbstractJournalReaderFactory implements JournalClosingListener {

    private final Map<JournalKey, Journal> readers = new HashMap<>();
    private final Map<JournalKey, JournalBulkReader> bulkReaders = new HashMap<>();
    private final List<Journal> journalList = new ArrayList<>();
    private JournalPool pool;

    public JournalCachingFactory(JournalConfiguration configuration) {
        super(configuration);
    }

    public JournalCachingFactory(JournalConfiguration configuration, TimerCache timerCache, JournalPool pool) {
        super(configuration, timerCache);
        this.pool = pool;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JournalBulkReader<T> bulkReader(JournalKey<T> key) throws JournalException {
        JournalBulkReader<T> result = bulkReaders.get(key);
        if (result == null) {
            result = new JournalBulkReader<>(getOrCreateMetadata(key), key, getTimerCache());
            result.setCloseListener(this);
            bulkReaders.put(key, result);
            journalList.add(result);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Journal<T> reader(JournalKey<T> key) throws JournalException {
        Journal<T> result = readers.get(key);
        if (result == null) {
            result = new Journal<>(getOrCreateMetadata(key), key, getTimerCache());
            result.setCloseListener(this);
            readers.put(key, result);
            journalList.add(result);
        }
        return result;
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.release(this);
        } else {
            for (int i = 0, sz = journalList.size(); i < sz; i++) {
                Journal journal = journalList.get(i);
                journal.setCloseListener(null);
                journal.close();
            }
            readers.clear();
            bulkReaders.clear();
        }
        super.close();
    }

    @Override
    public boolean closing(Journal journal) {
        return false;
    }

    public void refresh() throws JournalException {
        for (int i = 0, sz = journalList.size(); i < sz; i++) {
            journalList.get(i).refresh();
        }
    }

    void clearPool() {
        this.pool = null;
    }

    void expireOpenFiles() {
        for (int i = 0, sz = journalList.size(); i < sz; i++) {
            journalList.get(i).expireOpenFiles();
        }
    }
}
