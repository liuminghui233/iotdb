/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.apache.iotdb.db.sql.analyze;

import org.apache.iotdb.db.exception.query.PathNumOverLimitException;
import org.apache.iotdb.db.exception.sql.StatementAnalyzeException;
import org.apache.iotdb.db.sql.metadata.IMetadataFetcher;
import org.apache.iotdb.db.sql.rewriter.ConcatPathRewriter;
import org.apache.iotdb.db.sql.rewriter.DnfFilterOptimizer;
import org.apache.iotdb.db.sql.rewriter.MergeSingleFilterOptimizer;
import org.apache.iotdb.db.sql.rewriter.RemoveNotOptimizer;
import org.apache.iotdb.db.sql.statement.InsertStatement;
import org.apache.iotdb.db.sql.statement.QueryStatement;
import org.apache.iotdb.db.sql.statement.Statement;
import org.apache.iotdb.db.sql.statement.component.WhereCondition;
import org.apache.iotdb.db.sql.statement.filter.QueryFilter;
import org.apache.iotdb.db.sql.tree.StatementVisitor;

public class StatementAnalyzer {

  private final IMetadataFetcher metadataFetcher;
  private final Analysis analysis;
  private final AnalysisContext context;

  public StatementAnalyzer(
      IMetadataFetcher metadataFetcher, Analysis analysis, AnalysisContext context) {
    this.metadataFetcher = metadataFetcher;
    this.analysis = analysis;
    this.context = context;
  }

  public Analysis analyze(Statement statement) {
    return new AnalyzeVisitor().process(statement, context);
  }

  private final class AnalyzeVisitor extends StatementVisitor<Analysis, AnalysisContext> {

    @Override
    public Analysis visitStatement(Statement statement, AnalysisContext context) {
      analysis.setStatement(statement);
      return analysis;
    }

    @Override
    public Analysis visitQuery(QueryStatement queryStatement, AnalysisContext context) {
      try {
        // check for semantic errors
        queryStatement.selfCheck();

        // concat path and remove wildcards
        QueryStatement rewrittenStatement =
            (QueryStatement) new ConcatPathRewriter().rewrite(queryStatement, context);

        // TODO: check access permissions here

        // optimize expressions in whereCondition
        WhereCondition whereCondition = rewrittenStatement.getWhereCondition();
        if (whereCondition != null) {
          QueryFilter filter = whereCondition.getQueryFilter();
          filter = new RemoveNotOptimizer().optimize(filter);
          filter = new DnfFilterOptimizer().optimize(filter);
          filter = new MergeSingleFilterOptimizer().optimize(filter);
          whereCondition.setQueryFilter(filter);
        }
        analysis.setStatement(rewrittenStatement);
      } catch (StatementAnalyzeException | PathNumOverLimitException e) {
        e.printStackTrace();
      }
      return analysis;
    }

    @Override
    public Analysis visitInsert(InsertStatement insertStatement, AnalysisContext context) {
      // TODO: do analyze for insert statement
      analysis.setStatement(insertStatement);
      return analysis;
    }
  }
}
