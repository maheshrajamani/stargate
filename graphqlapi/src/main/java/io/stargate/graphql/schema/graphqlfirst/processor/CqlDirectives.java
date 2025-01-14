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
package io.stargate.graphql.schema.graphqlfirst.processor;

import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.schema.GraphQLSchema.newSchema;

import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableSet;
import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.stargate.db.query.Predicate;
import java.util.Set;
import org.apache.cassandra.stargate.db.ConsistencyLevel;

/**
 * Builds all the GraphQL directives that can be used in a deployed schema to customize the CQL
 * mapping.
 *
 * <p>{@link #ALL_AS_STRING} and {@link #ALL_AS_REGISTRY} can be used to get all the directives at
 * once. The other public constants represent the names of the directives and their arguments.
 */
public class CqlDirectives {

  private static final GraphQLEnumType ENTITY_TARGET_ENUM =
      newEnum()
          .name("EntityTarget")
          .description("The type of schema element a GraphQL object maps to")
          .value(EntityModel.Target.TABLE.name())
          .value(EntityModel.Target.UDT.name())
          .build();

  public static final String ENTITY = "cql_entity";
  public static final String ENTITY_NAME = "name";
  public static final String ENTITY_TARGET = "target";

  private static final GraphQLDirective ENTITY_DIRECTIVE =
      newDirective()
          .name(ENTITY)
          .description("Customizes the mapping of a GraphQL object to a CQL table or UDT")
          .argument(
              newArgument()
                  .name(ENTITY_NAME)
                  .type(Scalars.GraphQLString)
                  .description(
                      "A custom table or UDT name (otherwise it uses the same name as the object)")
                  .build())
          .argument(
              newArgument()
                  .name(ENTITY_TARGET)
                  .type(ENTITY_TARGET_ENUM)
                  .description("Whether the object maps to a CQL table (the default) or UDT")
                  .build())
          .validLocation(OBJECT)
          .build();

  public static final String INPUT = "cql_input";
  public static final String INPUT_NAME = "name";

  private static final GraphQLDirective INPUT_DIRECTIVE =
      newDirective()
          .name(INPUT)
          .description(
              "Annotates a GraphQL object to trigger the generation of a matching input type.\n"
                  + "The generated type will have the same fields (names and types), and can be "
                  + "referenced in mutations that target the corresponding CQL table or UDT.")
          .argument(
              newArgument()
                  .name(INPUT_NAME)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The name of the generated type.\n"
                          + "If not specified, it will be generated by appending 'Input' to the "
                          + "name of the original object")
                  .build())
          .validLocation(OBJECT)
          .build();

  private static final GraphQLEnumType CLUSTERING_ORDER_ENUM =
      newEnum()
          .name("ClusteringOrder")
          .description("The sorting order for clustering columns")
          .value("ASC")
          .value("DESC")
          .build();

  public static final String COLUMN = "cql_column";
  public static final String COLUMN_NAME = "name";
  public static final String COLUMN_PARTITION_KEY = "partitionKey";
  public static final String COLUMN_CLUSTERING_ORDER = "clusteringOrder";
  public static final String COLUMN_TYPE_HINT = "typeHint";

  private static final GraphQLDirective COLUMN_DIRECTIVE =
      newDirective()
          .name(COLUMN)
          .description("Customizes the mapping of a GraphQL field to a CQL column (or UDT field)")
          .argument(
              newArgument()
                  .name(COLUMN_NAME)
                  .type(Scalars.GraphQLString)
                  .description(
                      "A custom column name (otherwise it uses the same name as the field)")
                  .build())
          .argument(
              newArgument()
                  .name(COLUMN_PARTITION_KEY)
                  .type(Scalars.GraphQLBoolean)
                  .description("Whether the column forms part of the partition key")
                  .build())
          .argument(
              newArgument()
                  .name(COLUMN_CLUSTERING_ORDER)
                  .type(CLUSTERING_ORDER_ENUM)
                  .description(
                      "Whether the column is a clustering column, and if so in which order")
                  .build())
          .argument(
              newArgument()
                  .name(COLUMN_TYPE_HINT)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The CQL type to map to (e.g. `frozen<list<varchar>>`).\n"
                          + "Most of the time you don't need this, the CQL type will be inferred"
                          + " from the GraphQL type. It is only needed for fine control over the "
                          + "\"frozen-ness\" of columns, or if you want to map a GraphQL list to "
                          + "a CQL set (instead of a list).")
                  .build())
          .validLocation(FIELD_DEFINITION)
          .build();

  private static final GraphQLEnumType INDEX_TARGET_ENUM =
      newEnum()
          .name("IndexTarget")
          .description("Which part of a collection field will be indexed.")
          .value(
              newEnumValueDefinition()
                  .name(IndexTarget.VALUES.name())
                  .description(
                      "Indexes the values of a list field.\n"
                          + "This is only allowed if the CQL type is not frozen.")
                  .build())
          .value(
              newEnumValueDefinition()
                  .name(IndexTarget.FULL.name())
                  .description(
                      "Indexes the full collection for a list, set or map column.\n"
                          + "This is only allowed if the CQL type is frozen.")
                  .build())
          .build();

  public static final String INDEX = "cql_index";
  public static final String INDEX_NAME = "name";
  public static final String INDEX_CLASS = "class";
  public static final String INDEX_TARGET = "target";
  public static final String INDEX_OPTIONS = "options";

  private static final GraphQLDirective INDEX_DIRECTIVE =
      newDirective()
          .name(INDEX)
          .description(
              String.format(
                  "Requests the creation of a CQL index for a GraphQL object field.\n"
                      + "This is only allowed for objects that map to CQL tables, and only for "
                      + "non-partition-key fields (in other words, fields that have neither"
                      + "`@%1$s.%2$s` nor `@%1$s.%3$s set).",
                  COLUMN, COLUMN_PARTITION_KEY, COLUMN_CLUSTERING_ORDER))
          .argument(
              newArgument()
                  .name(INDEX_NAME)
                  .type(Scalars.GraphQLString)
                  .description(
                      "A custom name for the index. If not specified, one will be generated.")
                  .build())
          .argument(
              newArgument()
                  .name(INDEX_CLASS)
                  .type(Scalars.GraphQLString)
                  .description(
                      "If the index is custom, the name of the index class to use. If not "
                          + "specified, this will be a regular secondary index.")
                  .build())
          .argument(
              newArgument()
                  .name(INDEX_TARGET)
                  .type(INDEX_TARGET_ENUM)
                  .description(
                      "(Only used with list fields) Which part of the field to index. If not "
                          + "specified, this will default to `VALUES`.")
                  .build())
          .argument(
              newArgument()
                  .name(INDEX_OPTIONS)
                  .type(Scalars.GraphQLString)
                  .description(
                      "Any custom options to pass to the index, in the format: "
                          + "`'option1': 'value1', 'option2': 'value2'...`")
                  .build())
          .validLocation(FIELD_DEFINITION)
          .build();

  public static final String PAGING_STATE = "cql_pagingState";

  private static final GraphQLDirective PAGING_STATE_DIRECTIVE =
      newDirective()
          .name(PAGING_STATE)
          .description(
              "Annotates a query parameter to indicate that it will receive the paging state. "
                  + "That parameter must have type `String`.")
          .validLocation(ARGUMENT_DEFINITION)
          .build();

  public static final String PAYLOAD = "cql_payload";

  private static final GraphQLDirective PAYLOAD_DIRECTIVE =
      newDirective()
          .name(PAYLOAD)
          .description(
              "Indicates that a type represents a \"payload\" object that will be used as the "
                  + "argument or return type of a GraphQL operation. Such objects  are NOT mapped "
                  + "to a CQL table.")
          .validLocations(OBJECT, INPUT_OBJECT)
          .build();

  private static final GraphQLEnumType QUERY_CONSISTENCY_ENUM =
      newEnum()
          .name("QueryConsistency")
          .description("The consistency level of the CQL SELECT generated for a query.")
          .value(ConsistencyLevel.LOCAL_ONE.name())
          .value(ConsistencyLevel.LOCAL_QUORUM.name())
          .value(ConsistencyLevel.ALL.name())
          .value(ConsistencyLevel.SERIAL.name())
          .value(ConsistencyLevel.LOCAL_SERIAL.name())
          .build();

  public static final String SELECT = "cql_select";
  public static final String SELECT_LIMIT = "limit";
  public static final String SELECT_PAGE_SIZE = "pageSize";
  public static final String SELECT_CONSISTENCY_LEVEL = "consistencyLevel";

  private static final GraphQLDirective SELECT_DIRECTIVE =
      newDirective()
          .name(SELECT)
          .description(
              "Provides additional options to the CQL SELECT generated for a query.\n"
                  + "This is only required if you pass arguments to the directive. Otherwise, "
                  + "GraphQL queries are always mapped to SELECT implicitly.")
          .argument(
              newArgument()
                  .name(SELECT_LIMIT)
                  .type(Scalars.GraphQLInt)
                  .description("How many results to return overall.")
                  .build())
          .argument(
              newArgument()
                  .name(SELECT_PAGE_SIZE)
                  .type(Scalars.GraphQLInt)
                  .defaultValue(100)
                  .description(
                      "How many results to return at a time.\n"
                          + "If there are more, paging can be implemented by:\n"
                          + "\n"
                          + String.format(
                              "* wrapping the return type into an object annotated with @%s, "
                                  + "that defines a field `pagingState: String`;\n",
                              PAYLOAD)
                          + String.format(
                              "* adding a `String` parameter annotated with `@%s` to the query.\n",
                              PAGING_STATE)
                          + "\n"
                          + "Then the page state returned by each query can be reinjected into the "
                          + "next query to get the next page.")
                  .build())
          .argument(
              newArgument()
                  .name(SELECT_CONSISTENCY_LEVEL)
                  .type(QUERY_CONSISTENCY_ENUM)
                  .description("The consistency level to use.")
                  .defaultValue(ConsistencyLevel.LOCAL_QUORUM.name())
                  .build())
          .validLocation(FIELD_DEFINITION)
          .build();

  private static final GraphQLEnumType MUTATION_CONSISTENCY_ENUM =
      newEnum()
          .name("MutationConsistency")
          .description("The consistency level of the CQL query generated for a mutation.")
          .value(ConsistencyLevel.LOCAL_ONE.name())
          .value(ConsistencyLevel.LOCAL_QUORUM.name())
          .value(ConsistencyLevel.ALL.name())
          .build();
  private static final GraphQLEnumType SERIAL_CONSISTENCY_ENUM =
      newEnum()
          .name("SerialConsistency")
          .description("The serial consistency level of the CQL query generated for a mutation.")
          .value(ConsistencyLevel.SERIAL.name())
          .value(ConsistencyLevel.LOCAL_SERIAL.name())
          .build();

  public static final String INSERT = "cql_insert";
  public static final String INSERT_IF_NOT_EXISTS = "ifNotExists";
  public static final String UPDATE = "cql_update";
  public static final String UPDATE_OR_DELETE_TARGET_ENTITY = "targetEntity";
  public static final String UPDATE_OR_DELETE_IF_EXISTS = "ifExists";
  public static final String DELETE = "cql_delete";
  public static final String MUTATION_CONSISTENCY_LEVEL = "consistencyLevel";
  public static final String MUTATION_SERIAL_CONSISTENCY_LEVEL = "serialConsistency";
  public static final String UPDATE_OR_INSERT_TTL = "ttl";

  private static final GraphQLArgument MUTATION_CONSISTENCY_LEVEL_ARGUMENT =
      newArgument()
          .name(MUTATION_CONSISTENCY_LEVEL)
          .type(MUTATION_CONSISTENCY_ENUM)
          .description("The consistency level to use.")
          .defaultValue(ConsistencyLevel.LOCAL_QUORUM.name())
          .build();

  private static final GraphQLArgument MUTATION_SERIAL_CONSISTENCY_LEVEL_ARGUMENT =
      newArgument()
          .name(MUTATION_SERIAL_CONSISTENCY_LEVEL)
          .type(SERIAL_CONSISTENCY_ENUM)
          .description("The serial consistency level to use.")
          .defaultValue(ConsistencyLevel.SERIAL.name())
          .build();

  private static final GraphQLArgument UPDATE_OR_INSERT_TTL_ARGUMENT =
      newArgument()
          .name(UPDATE_OR_INSERT_TTL)
          .type(Scalars.GraphQLString)
          .description(
              "The TTL to use.\n"
                  + "If this is a raw integer, it will be interpreted as a number of seconds. "
                  + "Otherwise, it must be a valid ISO-8601 duration string (note that the "
                  + "minimum granularity is seconds, so if the duration has a nanosecond part it "
                  + "will be truncated). The value must be between 0 and 2^31- 1 (both included).")
          .build();

  private static final GraphQLDirective INSERT_DIRECTIVE =
      newDirective()
          .name(INSERT)
          .description(
              "Indicates that a mutation should be mapped to a CQL INSERT query.\n"
                  + "Note that this is not required if the mutation name starts with `insert` or "
                  + "`create`.")
          .argument(
              newArgument()
                  .name(INSERT_IF_NOT_EXISTS)
                  .type(Scalars.GraphQLBoolean)
                  .defaultValue(false)
                  .description(
                      "What to do if the entity already exists.\n\n"
                          + "* If `false`, the insert will overwrite any data that was already "
                          + "present.\n"
                          + "* If `true`, it won't, and the mutation will return the existing "
                          + "entity. In this case, it is strongly recommended to wrap the response "
                          + "in a payload object that also defines an `applied` field, for "
                          + "example: `type InsertUserResponse @cql_payload { applied: Boolean!, "
                          + "user: User! }`\n\n"
                          + "Note that setting this flag to `true` might increase the latency of "
                          + "the operation. It should not be used casually.\n"
                          + "By convention, this flag will be set automatically if the mutation "
                          + "name ends with `IfNotExists`.")
                  .build())
          .argument(MUTATION_CONSISTENCY_LEVEL_ARGUMENT)
          .argument(MUTATION_SERIAL_CONSISTENCY_LEVEL_ARGUMENT)
          .argument(UPDATE_OR_INSERT_TTL_ARGUMENT)
          .validLocation(FIELD_DEFINITION)
          .build();

  private static final GraphQLDirective UPDATE_DIRECTIVE =
      newDirective()
          .name(UPDATE)
          .description(
              "Indicates that a mutation should be mapped to a CQL UPDATE query.\n"
                  + "Note that this is not required if the mutation name starts with `update`.")
          .argument(
              newArgument()
                  .name(UPDATE_OR_DELETE_TARGET_ENTITY)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The name of the type to update.\n"
                          + "This is only needed if the mutation takes individual key fields as "
                          + "arguments (as opposed to an instance of the type).\n"
                          + "This must be a type that maps to a table.")
                  .build())
          .argument(
              newArgument()
                  .name(UPDATE_OR_DELETE_IF_EXISTS)
                  .type(Scalars.GraphQLBoolean)
                  .defaultValue(false)
                  .description(
                      "Whether to check if the entity exists before updating.\n\n"
                          + "Update mutations return the outcome of the update, either directly if "
                          + "the mutation returns `Boolean`, or via the `applied` field if the "
                          + "mutation returns a response payload object.\n\n"
                          + "* If `ifExists` is `false`, the mutation will always return `true`, "
                          + "whether it actually updated something or not.\n"
                          + "* If `ifExists` is `true`, the mutation will return `true` if it "
                          + "updated something, and `false`otherwise.\n\n"
                          + "Note that setting this flag to `true` might increase the latency of "
                          + "the operation. It should not be used casually.\n"
                          + "By convention, this flag will be set automatically if the mutation "
                          + "name ends with `IfExists`.")
                  .build())
          .argument(MUTATION_CONSISTENCY_LEVEL_ARGUMENT)
          .argument(MUTATION_SERIAL_CONSISTENCY_LEVEL_ARGUMENT)
          .argument(UPDATE_OR_INSERT_TTL_ARGUMENT)
          .validLocation(FIELD_DEFINITION)
          .build();

  private static final GraphQLDirective DELETE_DIRECTIVE =
      newDirective()
          .name(DELETE)
          .description(
              "Indicates that a mutation should be mapped to a CQL DELETE query.\n"
                  + "Note that this is not required if the mutation name starts with `delete` or "
                  + "`remove`.")
          .argument(
              newArgument()
                  .name(UPDATE_OR_DELETE_TARGET_ENTITY)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The name of the type to delete.\n"
                          + "This is only needed if the mutation takes individual key fields as "
                          + "arguments (as opposed to an instance of the type).\n"
                          + "This must be a type that maps to a table.")
                  .build())
          .argument(
              newArgument()
                  .name(UPDATE_OR_DELETE_IF_EXISTS)
                  .type(Scalars.GraphQLBoolean)
                  .defaultValue(false)
                  .description(
                      "Whether to check if the entity exists before deleting.\n\n"
                          + "Delete mutations return the outcome of the deletion, either directly "
                          + "if the mutation returns `Boolean`, or via the `applied` field if the "
                          + "mutation returns a response payload object.\n\n"
                          + "* If `ifExists` is `false`, the mutation will always return `true`, "
                          + "whether it actually deleted something or not.\n"
                          + "* If `ifExists` is `true`, the mutation will return `true` if it "
                          + "deleted something, and `false` otherwise.\n\n"
                          + "Note that setting this flag to `true` might increase the latency of "
                          + "the operation. It should not be used casually.\n"
                          + "By convention, this flag will be set automatically if the mutation "
                          + "name ends with `IfExists`.")
                  .build())
          .argument(MUTATION_CONSISTENCY_LEVEL_ARGUMENT)
          .argument(MUTATION_SERIAL_CONSISTENCY_LEVEL_ARGUMENT)
          .validLocation(FIELD_DEFINITION)
          .build();

  public static final String WHERE = "cql_where";
  public static final String IF = "cql_if";
  public static final String INCREMENT = "cql_increment";
  public static final String WHERE_OR_IF_OR_INCREMENT_FIELD = "field";
  public static final String WHERE_OR_IF_PREDICATE = "predicate";
  public static final String INCREMENT_PREPEND = "prepend";
  public static final String TIMESTAMP = "cql_timestamp";

  private static final GraphQLEnumType PREDICATE_ENUM =
      newEnum()
          .name("Predicate")
          .description(String.format("A predicate used in `@%s` to define a condition.", WHERE))
          .value(Predicate.EQ.name())
          .value(Predicate.IN.name())
          .value(Predicate.LT.name())
          .value(Predicate.GT.name())
          .value(Predicate.LTE.name())
          .value(Predicate.GTE.name())
          .value(Predicate.CONTAINS.name())
          .build();

  private static final GraphQLDirective WHERE_DIRECTIVE =
      newDirective()
          .name(WHERE)
          .description(
              "Annotates a parameter to customize the WHERE condition that is generated from it.")
          .argument(
              newArgument()
                  .name(WHERE_OR_IF_OR_INCREMENT_FIELD)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The name of the field that the condition applies to (if absent, it will be "
                          + "the name of the argument).")
                  .build())
          .argument(
              newArgument()
                  .name(WHERE_OR_IF_PREDICATE)
                  .type(PREDICATE_ENUM)
                  .defaultValue(Predicate.EQ.name())
                  .description("The predicate to use for the condition.")
                  .build())
          .validLocation(ARGUMENT_DEFINITION)
          .build();

  private static final GraphQLDirective INCREMENT_DIRECTIVE =
      newDirective()
          .name(INCREMENT)
          .description(
              "Annotates a parameter to indicate that it will be incremented.\n"
                  + "It is supported on the counter, set and list types.\n"
                  + "This is only allowed for update mutations.")
          .argument(
              newArgument()
                  .name(WHERE_OR_IF_OR_INCREMENT_FIELD)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The name of the field that the increment applies to (if absent, it will be "
                          + "the name of the argument).")
                  .build())
          .argument(
              newArgument()
                  .name(INCREMENT_PREPEND)
                  .type(Scalars.GraphQLBoolean)
                  .defaultValue(false)
                  .description(
                      "Specifies whether the value should be appended or prepended.\n"
                          + "It applies only to list. The default is false, meaning that the value will be appended.")
                  .build())
          .validLocation(ARGUMENT_DEFINITION)
          .build();

  private static final GraphQLEnumType IF_PREDICATE_ENUM =
      newEnum()
          .name("IfPredicate")
          .description(String.format("A predicate used in `@%s` to define a condition.", IF))
          .value(Predicate.EQ.name())
          .value(Predicate.NEQ.name())
          .value(Predicate.IN.name())
          .value(Predicate.LT.name())
          .value(Predicate.GT.name())
          .value(Predicate.LTE.name())
          .value(Predicate.GTE.name())
          .build();

  private static final GraphQLDirective IF_DIRECTIVE =
      newDirective()
          .name(IF)
          .description(
              "Annotates a parameter to indicate that it will be used as a condition that must "
                  + "test true on the selected entity in order for the mutation to be applied.\n\n"
                  + "This is only allowed for delete and update mutations.")
          .argument(
              newArgument()
                  .name(WHERE_OR_IF_OR_INCREMENT_FIELD)
                  .type(Scalars.GraphQLString)
                  .description(
                      "The name of the field that the condition applies to (if absent, it will be "
                          + "the name of the argument).")
                  .build())
          .argument(
              newArgument()
                  .name(WHERE_OR_IF_PREDICATE)
                  .type(IF_PREDICATE_ENUM)
                  .defaultValue(Predicate.EQ.name())
                  .description("The predicate to use for the condition.")
                  .build())
          .validLocation(ARGUMENT_DEFINITION)
          .build();

  private static final GraphQLDirective TIMESTAMP_DIRECTIVE =
      newDirective()
          .name(TIMESTAMP)
          .description(
              "Annotates a parameter to indicate that it will be used as a write timestamp for this row."
                  + "This is only allowed for insert and update mutations. The parameter can be "
                  + "either a `BigInt` (that represents a number of microseconds since the epoch), "
                  + "or a `String` (that represents an ISO-8601 zoned date time, e.g. "
                  + "`2007-12-03T10:15:30+01:00`).")
          .validLocation(ARGUMENT_DEFINITION)
          .build();

  public static final String ALL_AS_STRING;
  public static final TypeDefinitionRegistry ALL_AS_REGISTRY;

  static {
    // We need a query type in order to build a valid schema:
    GraphQLObjectType dummyQueryType =
        GraphQLObjectType.newObject()
            .name("Query")
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("dummy")
                    .type(Scalars.GraphQLBoolean)
                    .build())
            .build();
    GraphQLSchema schema =
        newSchema()
            .additionalDirective(ENTITY_DIRECTIVE)
            .additionalDirective(INPUT_DIRECTIVE)
            .additionalDirective(COLUMN_DIRECTIVE)
            .additionalDirective(INDEX_DIRECTIVE)
            .additionalDirective(PAGING_STATE_DIRECTIVE)
            .additionalDirective(PAYLOAD_DIRECTIVE)
            .additionalDirective(SELECT_DIRECTIVE)
            .additionalDirective(INSERT_DIRECTIVE)
            .additionalDirective(UPDATE_DIRECTIVE)
            .additionalDirective(DELETE_DIRECTIVE)
            .additionalDirective(WHERE_DIRECTIVE)
            .additionalDirective(IF_DIRECTIVE)
            .additionalDirective(INCREMENT_DIRECTIVE)
            .additionalDirective(TIMESTAMP_DIRECTIVE)
            .query(dummyQueryType)
            .build();

    // The printer adds these default directives, but we only want ours:
    Set<String> defaultDirectives = ImmutableSet.of("include", "skip", "deprecated", "specifiedBy");

    ALL_AS_STRING =
        new SchemaPrinter(
                SchemaPrinter.Options.defaultOptions()
                    .includeDirectives(d -> !defaultDirectives.contains(d.getName()))
                    .includeSchemaElement(e -> e != dummyQueryType))
            .print(schema);

    ALL_AS_REGISTRY = new SchemaParser().parse(ALL_AS_STRING);
  }
}
