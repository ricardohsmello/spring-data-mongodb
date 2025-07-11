/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.data.mongodb.core;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import reactor.test.StepVerifier;

import java.util.Objects;
import java.util.Optional;

import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.test.util.Client;

import com.mongodb.client.MongoClient;

/**
 * Integration tests for {@link ReactiveUpdateOperationSupport}.
 *
 * @author Mark Paluch
 */

class ReactiveUpdateOperationSupportTests {

	private static final String STAR_WARS = "star-wars";
	private static @Client MongoClient client;
	private static @Client com.mongodb.reactivestreams.client.MongoClient reactiveClient;

	private MongoTemplate blocking;
	private ReactiveMongoTemplate template;

	private Person han;
	private Person luke;

	@BeforeEach
	void setUp() {

		blocking = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "ExecutableUpdateOperationSupportTests"));
		blocking.dropCollection(STAR_WARS);

		han = new Person();
		han.firstname = "han";
		han.id = "id-1";

		luke = new Person();
		luke.firstname = "luke";
		luke.id = "id-2";

		blocking.save(han);
		blocking.save(luke);

		template = new ReactiveMongoTemplate(reactiveClient, "ExecutableUpdateOperationSupportTests");
	}

	@Test // DATAMONGO-1719
	void domainTypeIsRequired() {
		assertThatIllegalArgumentException().isThrownBy(() -> template.update(null));
	}

	@Test // DATAMONGO-1719
	void updateIsRequired() {
		assertThatIllegalArgumentException().isThrownBy(() -> template.update(Person.class).apply(null));
	}

	@Test // DATAMONGO-1719
	void collectionIsRequiredOnSet() {
		assertThatIllegalArgumentException().isThrownBy(() -> template.update(Person.class).inCollection(null));
	}

	@Test // DATAMONGO-1719
	void findAndModifyOptionsAreRequiredOnSet() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> template.update(Person.class).apply(new Update()).withOptions(null));
	}

	@Test // DATAMONGO-1719
	void updateFirst() {

		template.update(Person.class).apply(new Update().set("firstname", "Han")).first().as(StepVerifier::create)
				.consumeNextWith(actual -> {

					assertThat(actual.getModifiedCount()).isEqualTo(1L);
					assertThat(actual.getUpsertedId()).isNull();
				}).verifyComplete();

	}

	@Test // DATAMONGO-1719
	void updateAll() {

		template.update(Person.class).apply(new Update().set("firstname", "Han")).all().as(StepVerifier::create)
				.consumeNextWith(actual -> {

					assertThat(actual.getModifiedCount()).isEqualTo(2L);
					assertThat(actual.getUpsertedId()).isNull();
				}).verifyComplete();
	}

	@Test // DATAMONGO-1719
	void updateAllMatching() {

		template.update(Person.class).matching(queryHan()).apply(new Update().set("firstname", "Han")).all()
				.as(StepVerifier::create).consumeNextWith(actual -> {

					assertThat(actual.getModifiedCount()).isEqualTo(1L);
					assertThat(actual.getUpsertedId()).isNull();
				}).verifyComplete();
	}

	@Test // DATAMONGO-2416
	void updateAllMatchingCriteria() {

		template.update(Person.class).matching(where("id").is(han.getId())).apply(new Update().set("firstname", "Han"))
				.all().as(StepVerifier::create).consumeNextWith(actual -> {

					assertThat(actual.getModifiedCount()).isEqualTo(1L);
					assertThat(actual.getUpsertedId()).isNull();
				}).verifyComplete();
	}

	@Test // DATAMONGO-1719
	void updateWithDifferentDomainClassAndCollection() {

		template.update(Jedi.class).inCollection(STAR_WARS).matching(query(where("_id").is(han.getId())))
				.apply(new Update().set("name", "Han")).all().as(StepVerifier::create).consumeNextWith(actual -> {

					assertThat(actual.getModifiedCount()).isEqualTo(1L);
					assertThat(actual.getUpsertedId()).isNull();
				}).verifyComplete();

		assertThat(blocking.findOne(queryHan(), Person.class)).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname",
				"Han");
	}

	@Test // DATAMONGO-1719
	void findAndModify() {

		template.update(Person.class).matching(queryHan()).apply(new Update().set("firstname", "Han")).findAndModify()
				.as(StepVerifier::create).expectNext(han).verifyComplete();

		assertThat(blocking.findOne(queryHan(), Person.class)).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname",
				"Han");
	}

	@Test // DATAMONGO-1719
	void findAndModifyWithDifferentDomainTypeAndCollection() {

		template.update(Jedi.class).inCollection(STAR_WARS).matching(query(where("_id").is(han.getId())))
				.apply(new Update().set("name", "Han")).findAndModify().as(StepVerifier::create)
				.consumeNextWith(actual -> assertThat(actual.getName()).isEqualTo("han")).verifyComplete();

		assertThat(blocking.findOne(queryHan(), Person.class)).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname",
				"Han");
	}

	@Test // GH-4949
	void findAndModifyWithWithResultConversion() {

		template.update(Jedi.class).inCollection(STAR_WARS).matching(query(where("_id").is(han.getId())))
				.apply(new Update().set("name", "Han")).map((raw, it) -> Optional.of(it.get())).findAndModify()
				.as(StepVerifier::create).consumeNextWith(actual -> assertThat(actual.get().getName()).isEqualTo("han"))
				.verifyComplete();

		assertThat(blocking.findOne(queryHan(), Person.class)).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname",
				"Han");
	}

	@Test // DATAMONGO-1719
	void findAndModifyWithOptions() {

		template.update(Person.class).matching(queryHan()).apply(new Update().set("firstname", "Han"))
				.withOptions(FindAndModifyOptions.options().returnNew(true)).findAndModify().as(StepVerifier::create)
				.consumeNextWith(actual -> {

					assertThat(actual).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname", "Han");
				}).verifyComplete();
	}

	@Test // DATAMONGO-1719
	void upsert() {

		template.update(Person.class).matching(query(where("id").is("id-3")))
				.apply(new Update().set("firstname", "Chewbacca")).upsert().as(StepVerifier::create).consumeNextWith(actual -> {

					assertThat(actual.getModifiedCount()).isEqualTo(0L);
					assertThat(actual.getUpsertedId()).isEqualTo(new BsonString("id-3"));
				}).verifyComplete();
	}

	@Test // DATAMONGO-1827
	void findAndReplace() {

		Person luke = new Person();
		luke.firstname = "Luke";

		template.update(Person.class).matching(queryHan()).replaceWith(luke).findAndReplace() //
				.as(StepVerifier::create).expectNext(han).verifyComplete();

		template.findOne(queryHan(), Person.class) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {
					assertThat(actual).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname", "Luke");
				}).verifyComplete();
	}

	@Test // DATAMONGO-1827
	void findAndReplaceWithProjection() {

		Person luke = new Person();
		luke.firstname = "Luke";

		template.update(Person.class).matching(queryHan()).replaceWith(luke).as(Jedi.class).findAndReplace() //
				.as(StepVerifier::create).consumeNextWith(it -> {
					assertThat(it.getName()).isEqualTo(han.firstname);
				}).verifyComplete();
	}

	@Test // GH-4949
	void findAndReplaceWithResultConversion() {

		Person luke = new Person();
		luke.firstname = "Luke";

		template.update(Person.class).matching(queryHan()).replaceWith(luke).map((raw, it) -> Optional.of(it.get())).findAndReplace() //
			.as(StepVerifier::create).consumeNextWith(it -> {
				assertThat(it.get().getFirstname()).isEqualTo(han.firstname);
			}).verifyComplete();
	}

	@Test // DATAMONGO-1827
	void findAndReplaceWithCollection() {

		Person luke = new Person();
		luke.firstname = "Luke";

		template.update(Person.class).inCollection(STAR_WARS).matching(queryHan()).replaceWith(luke).findAndReplace() //
				.as(StepVerifier::create).expectNext(han).verifyComplete();

		template.findOne(queryHan(), Person.class) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {
					assertThat(actual).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname", "Luke");
				}).verifyComplete();
	}

	@Test // DATAMONGO-1827
	void findAndReplaceWithOptions() {

		Person luke = new Person();
		luke.firstname = "Luke";

		template.update(Person.class).matching(queryHan()).replaceWith(luke)
				.withOptions(FindAndReplaceOptions.options().returnNew()).findAndReplace() //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {
					assertThat(actual).isNotEqualTo(han).hasFieldOrPropertyWithValue("firstname", "Luke");
				}).verifyComplete();
	}

	private Query queryHan() {
		return query(where("id").is(han.getId()));
	}

	@org.springframework.data.mongodb.core.mapping.Document(collection = STAR_WARS)
	static class Person {

		@Id String id;
		String firstname;

		public String getId() {
			return this.id;
		}

		public String getFirstname() {
			return this.firstname;
		}

		public void setId(String id) {
			this.id = id;
		}

		public void setFirstname(String firstname) {
			this.firstname = firstname;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Person person = (Person) o;
			return Objects.equals(id, person.id) && Objects.equals(firstname, person.firstname);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, firstname);
		}

		public String toString() {
			return "ReactiveUpdateOperationSupportTests.Person(id=" + this.getId() + ", firstname=" + this.getFirstname()
					+ ")";
		}
	}

	static class Human {

		@Id String id;

		public String getId() {
			return this.id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String toString() {
			return "ReactiveUpdateOperationSupportTests.Human(id=" + this.getId() + ")";
		}
	}

	static class Jedi {

		@Field("firstname") String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Jedi jedi = (Jedi) o;
			return Objects.equals(name, jedi.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name);
		}

		public String toString() {
			return "ReactiveUpdateOperationSupportTests.Jedi(name=" + this.getName() + ")";
		}
	}
}
