/*
* Copyright 2020 Expedia, Inc.
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

package com.vrbo.jarviz.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.vrbo.jarviz.model.CouplingRecord;
import com.vrbo.jarviz.util.JsonUtils;

/**
 * Simple JSON blob writer for the {@link CouplingRecord}. The resultant output
 * will a newline-delimited JSON file (.jsonl). This is not thread safe, should
 * not be shared among multiple threads. See http://jsonlines.org
 */
public class CouplingRecordWriter {

    private enum TYPE {
        CSV, JSONL
    }

    private final String filePath;

    private Writer writer = null;

    private CsvMapper mapper = new CsvMapper();
    private CsvSchema schema = mapper.schemaFor(CouplingRecord.class).withHeader();
    private SequenceWriter sequenceWriter;
    private TYPE resultType = TYPE.JSONL;

    public CouplingRecordWriter(@Nonnull final String filePath) {
        this.filePath = filePath;
        if (this.filePath.endsWith("csv")) {
            this.resultType = TYPE.CSV;
        }
    }

    public void writeResult(final CouplingRecord couplingRecord) {
        if (resultType == TYPE.JSONL) {
            writeAsJson(couplingRecord);
        } else if (resultType == TYPE.CSV) {
            writeAsCsv(couplingRecord);
        }
    }

    /**
     * Writes the given coupling record as a JSON blob into the file.
     *
     * @param couplingRecord The coupling record.
     */
    private void writeAsJson(final CouplingRecord couplingRecord) {
        if (writer == null) {
            openFileStream();
        }

        try {
            writer.write(JsonUtils.toJsonString(couplingRecord));
            writer.write('\n');
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Unable to generate CouplingRecord file: %s", filePath), e);
        }
    }

    private void writeAsCsv(final CouplingRecord couplingRecord) {
        try {
            if (writer == null) {
                openFileStream();
                sequenceWriter = mapper.writer(schema).writeValues(writer);
            }
            sequenceWriter.write(couplingRecord);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Unable to generate CouplingRecord file: %s", filePath), e);
        }

    }

    private void openFileStream() {
        try {
            writer = new PrintWriter(filePath, "UTF-8");
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot write to file %s", filePath), e);
        }
    }

    /**
     * Closes the underlying file stream and return a boolean to indicate if the
     * operation was successful.
     *
     * @return Indicates if the close operation was successful.
     */
    public boolean close() {
        if (writer == null) {
            return false;
        }

        try {
            writer.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to properly close the file stream", e);
        }

        return true;
    }

}
