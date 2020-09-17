/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.web3signer.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.Arguments;
import org.jdbi.v3.core.mapper.ColumnMappers;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.testing.JdbiRule;
import org.jdbi.v3.testing.Migration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

// This must be a junit4 for the JdbiRule to work
public class ValidatorsDaoTest {

  @Rule
  public JdbiRule postgres =
      JdbiRule.embeddedPostgres()
          .withMigration(Migration.before().withPath("migrations/postgresql"));

  @Before
  public void setup() {
    postgres.getJdbi().installPlugin(new SqlObjectPlugin());
    postgres.getJdbi().getConfig(Arguments.class).register(new BytesArgumentFactory());
    postgres.getJdbi().getConfig(ColumnMappers.class).register(new BytesColumnMapper());
  }

  @Test
  public void retrievesSpecifiedValidatorsFromDb() {
    final Jdbi jdbi = postgres.getJdbi();
    jdbi.useHandle(
        h -> {
          insertValidator(h, 100);
          insertValidator(h, 101);
          insertValidator(h, 102);
        });

    jdbi.useExtension(
        ValidatorsDao.class,
        dao -> {
          final List<Validator> registeredValidators =
              dao.retrieveValidators(List.of(Bytes.of(101), Bytes.of(102)));
          assertThat(registeredValidators).hasSize(2);
          assertThat(registeredValidators.get(0))
              .isEqualToComparingFieldByField(new Validator(2, Bytes.of(101)));
          assertThat(registeredValidators.get(1))
              .isEqualToComparingFieldByField(new Validator(3, Bytes.of(102)));
        });
  }

  @Test
  public void storesValidatorsInDb() {
    final Jdbi jdbi = postgres.getJdbi();

    jdbi.useExtension(
        ValidatorsDao.class, dao -> dao.registerValidators(List.of(Bytes.of(101), Bytes.of(102))));

    final List<Validator> validators =
        jdbi.withHandle(
            h ->
                h.createQuery("SELECT * FROM validators ORDER BY ID")
                    .mapToBean(Validator.class)
                    .list());
    assertThat(validators.size()).isEqualTo(2);
    assertThat(validators.get(0)).isEqualToComparingFieldByField(new Validator(1, Bytes.of(101)));
    assertThat(validators.get(1)).isEqualToComparingFieldByField(new Validator(2, Bytes.of(102)));
  }

  @Test
  public void storesUnregisteredValidatorsInDb() {
    final Jdbi jdbi = postgres.getJdbi();
    jdbi.useHandle(
        h -> {
          insertValidator(h, 100);
          insertValidator(h, 101);
          insertValidator(h, 102);
        });

    jdbi.useExtension(
        ValidatorsDao.class,
        dao ->
            dao.registerMissingValidators(
                List.of(Bytes.of(101), Bytes.of(102), Bytes.of(103), Bytes.of(104))));

    final List<Validator> validators =
        jdbi.withHandle(
            h ->
                h.createQuery("SELECT * FROM validators ORDER BY ID")
                    .mapToBean(Validator.class)
                    .list());
    assertThat(validators.size()).isEqualTo(5);
    assertThat(validators.get(0)).isEqualToComparingFieldByField(new Validator(1, Bytes.of(100)));
    assertThat(validators.get(1)).isEqualToComparingFieldByField(new Validator(2, Bytes.of(101)));
    assertThat(validators.get(2)).isEqualToComparingFieldByField(new Validator(3, Bytes.of(102)));
    assertThat(validators.get(3)).isEqualToComparingFieldByField(new Validator(4, Bytes.of(103)));
    assertThat(validators.get(4)).isEqualToComparingFieldByField(new Validator(5, Bytes.of(104)));
  }

  private void insertValidator(final Handle h, final int i) {
    final byte[] value = Bytes.of(i).toArrayUnsafe();
    h.execute("INSERT INTO validators (public_key) VALUES (?)", value);
  }
}
