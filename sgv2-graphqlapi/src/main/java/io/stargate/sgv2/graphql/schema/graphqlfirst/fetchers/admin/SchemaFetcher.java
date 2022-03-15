/*
 * Copyright The Stargate Authors
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
package io.stargate.sgv2.graphql.schema.graphqlfirst.fetchers.admin;

import graphql.schema.DataFetchingEnvironment;
import io.stargate.proto.Schema;
import io.stargate.sgv2.common.grpc.SchemaReads;
import io.stargate.sgv2.common.grpc.StargateBridgeClient;
import io.stargate.sgv2.graphql.schema.CassandraFetcher;

public abstract class SchemaFetcher<ResultT> extends CassandraFetcher<ResultT> {

  protected void authorize(StargateBridgeClient bridge, String keyspace) {
    // Check that the user has access to the keyspace. We don't want to let them see the GraphQL
    // schema for something that's forbidden to them.
    if (!bridge.authorizeSchemaRead(
        SchemaReads.keyspace(keyspace, Schema.SchemaRead.SourceApi.GRAPHQL))) {
      throw new IllegalArgumentException(String.format("Keyspace '%s' does not exist.", keyspace));
    }
  }

  protected String getKeyspace(DataFetchingEnvironment environment, StargateBridgeClient bridge) {
    String keyspace = environment.getArgument("keyspace");
    if (!bridge.getKeyspace(keyspace, true).isPresent()) {
      throw new IllegalArgumentException(String.format("Keyspace '%s' does not exist.", keyspace));
    }
    return keyspace;
  }
}
