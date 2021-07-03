/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch.repository.query;

import static org.assertj.core.api.Assertions.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.lang.Nullable;

/**
 * @author Christoph Strobl
 * @author Peter-Josef Meisch
 * @author Niklas Herder
 */
@RunWith(MockitoJUnitRunner.class)
public class ElasticsearchStringQueryUnitTests {

	@Mock ElasticsearchOperations operations;
	ElasticsearchConverter converter;

	@Before
	public void setUp() {
		converter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
	}

	@Test // DATAES-552
	public void shouldReplaceParametersCorrectly() throws Exception {

		org.springframework.data.elasticsearch.core.query.Query query = createQuery("findByName", "Luke");

		assertThat(query).isInstanceOf(StringQuery.class);
		assertThat(((StringQuery) query).getSource())
				.isEqualTo("{ 'bool' : { 'must' : { 'term' : { 'name' : 'Luke' } } } }");
	}

	@Test // DATAES-552
	public void shouldReplaceRepeatedParametersCorrectly() throws Exception {

		org.springframework.data.elasticsearch.core.query.Query query = createQuery("findWithRepeatedPlaceholder", "zero",
				"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven");

		assertThat(query).isInstanceOf(StringQuery.class);
		assertThat(((StringQuery) query).getSource())
				.isEqualTo("name:(zero, eleven, one, two, three, four, five, six, seven, eight, nine, ten, eleven, zero, one)");
	}

	@Test // #1790
	public void shouldEscapeStringsInQueryParameters() throws Exception {

		org.springframework.data.elasticsearch.core.query.Query query = createQuery("findByPrefix", "hello \"Stranger\"");

		assertThat(query).isInstanceOf(StringQuery.class);
		assertThat(((StringQuery) query).getSource())
				.isEqualTo("{\"bool\":{\"must\": [{\"match\": {\"prefix\": {\"name\" : \"hello \\\"Stranger\\\"\"}}]}}");
	}

	@Test // #1858
	public void shouldOnlyEscapeStringQueryParameters() throws Exception {
		org.springframework.data.elasticsearch.core.query.Query query = createQuery("findByAge", Integer.valueOf(30));

		assertThat(query).isInstanceOf(StringQuery.class);
		assertThat(((StringQuery) query).getSource()).isEqualTo("{ 'bool' : { 'must' : { 'term' : { 'age' : 30 } } } }");

	}

	@Test // #1858
	public void shouldOnlyEscapeStringCollectionQueryParameters() throws Exception {
		org.springframework.data.elasticsearch.core.query.Query query = createQuery("findByAgeIn",
				new ArrayList<>(Arrays.asList(30, 35, 40)));

		assertThat(query).isInstanceOf(StringQuery.class);
		assertThat(((StringQuery) query).getSource())
				.isEqualTo("{ 'bool' : { 'must' : { 'term' : { 'age' : [30,35,40] } } } }");

	}

	@Test // #1858
	public void shouldEscapeStringsInCollectionsQueryParameters() throws Exception {

		final List<String> another_string = Arrays.asList("hello \"Stranger\"", "Another string");
		List<String> params = new ArrayList<>(another_string);
		org.springframework.data.elasticsearch.core.query.Query query = createQuery("findByNameIn", params);

		assertThat(query).isInstanceOf(StringQuery.class);
		assertThat(((StringQuery) query).getSource()).isEqualTo(
				"{ 'bool' : { 'must' : { 'terms' : { 'name' : [\"hello \\\"Stranger\\\"\",\"Another string\"] } } } }");
	}

	private org.springframework.data.elasticsearch.core.query.Query createQuery(String methodName, Object... args)
			throws NoSuchMethodException {

		Class<?>[] argTypes = Arrays.stream(args).map(Object::getClass).toArray(size -> new Class[size]);
		ElasticsearchQueryMethod queryMethod = getQueryMethod(methodName, argTypes);
		ElasticsearchStringQuery elasticsearchStringQuery = queryForMethod(queryMethod);
		return elasticsearchStringQuery.createQuery(new ElasticsearchParametersParameterAccessor(queryMethod, args));
	}

	private ElasticsearchStringQuery queryForMethod(ElasticsearchQueryMethod queryMethod) {
		return new ElasticsearchStringQuery(queryMethod, operations, queryMethod.getAnnotatedQuery());
	}

	private ElasticsearchQueryMethod getQueryMethod(String name, Class<?>... parameters) throws NoSuchMethodException {

		Method method = SampleRepository.class.getMethod(name, parameters);
		return new ElasticsearchQueryMethod(method, new DefaultRepositoryMetadata(SampleRepository.class),
				new SpelAwareProxyProjectionFactory(), converter.getMappingContext());
	}

	private interface SampleRepository extends Repository<Person, String> {

		@Query("{ 'bool' : { 'must' : { 'term' : { 'age' : ?0 } } } }")
		List<Person> findByAge(Integer age);

		@Query("{ 'bool' : { 'must' : { 'term' : { 'age' : ?0 } } } }")
		List<Person> findByAgeIn(ArrayList<Integer> age);

		@Query("{ 'bool' : { 'must' : { 'term' : { 'name' : '?0' } } } }")
		Person findByName(String name);

		@Query("{ 'bool' : { 'must' : { 'terms' : { 'name' : ?0 } } } }")
		Person findByNameIn(ArrayList<String> names);

		@Query(value = "name:(?0, ?11, ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?0, ?1)")
		Person findWithRepeatedPlaceholder(String arg0, String arg1, String arg2, String arg3, String arg4, String arg5,
				String arg6, String arg7, String arg8, String arg9, String arg10, String arg11);

		@Query("{\"bool\":{\"must\": [{\"match\": {\"prefix\": {\"name\" : \"?0\"}}]}}")
		List<Book> findByPrefix(String prefix);
	}

	/**
	 * @author Rizwan Idrees
	 * @author Mohsin Husen
	 * @author Artur Konczak
   * @author Niklas Herder
	 */

	@Document(indexName = "test-index-person-query-unittest", type = "user", shards = 1, replicas = 0,
			refreshInterval = "-1")
	static class Person {

		@Nullable public int age;
		@Nullable @Id private String id;
		@Nullable private String name;
		@Nullable @Field(type = FieldType.Nested) private List<Car> car;
		@Nullable @Field(type = FieldType.Nested, includeInParent = true) private List<Book> books;

		@Nullable
		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<Car> getCar() {
			return car;
		}

		public void setCar(List<Car> car) {
			this.car = car;
		}

		public List<Book> getBooks() {
			return books;
		}

		public void setBooks(List<Book> books) {
			this.books = books;
		}
	}

	/**
	 * @author Rizwan Idrees
	 * @author Mohsin Husen
	 * @author Nordine Bittich
	 */
	@Setter
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@Document(indexName = "test-index-book-query-unittest", type = "book", shards = 1, replicas = 0,
			refreshInterval = "-1")
	static class Book {

		@Id private String id;
		private String name;
		@Field(type = FieldType.Object) private Author author;
		@Field(type = FieldType.Nested) private Map<Integer, Collection<String>> buckets = new HashMap<>();
		@MultiField(mainField = @Field(type = FieldType.Text, analyzer = "whitespace"),
				otherFields = { @InnerField(suffix = "prefix", type = FieldType.Text, analyzer = "stop",
						searchAnalyzer = "standard") }) private String description;
	}

	/**
	 * @author Rizwan Idrees
	 * @author Mohsin Husen
	 * @author Artur Konczak
	 */
	@Setter
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	static class Car {

		private String name;
		private String model;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}
	}

	/**
	 * @author Rizwan Idrees
	 * @author Mohsin Husen
	 */
	static class Author {

		private String id;
		private String name;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}
