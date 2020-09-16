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

import tech.pegasys.web3signer.slashingprotection.dao.SignedBlock;
import tech.pegasys.web3signer.slashingprotection.dao.SignedBlocksDao;
import tech.pegasys.web3signer.slashingprotection.dao.Validator;
import tech.pegasys.web3signer.slashingprotection.dao.ValidatorsDao;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.jdbi.v3.core.Jdbi;

public class DbSlashingProtection implements SlashingProtection {
  private final Jdbi jdbi;

  public DbSlashingProtection(final Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  @Override
  public boolean maySignAttestation(
      final String publicKey,
      final Bytes signingRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return true;
  }

  @Override
  public boolean maySignBlock(
      final String publicKey, final Bytes signingRoot, final UInt64 blockSlot) {
    final Bytes publicKeyBytes = Bytes.fromHexString(publicKey);
    final List<Validator> validators =
        jdbi.withExtension(
            ValidatorsDao.class, dao -> dao.retrieveValidators(List.of(publicKeyBytes)));
    final Optional<Long> validatorId = validators.stream().findFirst().map(Validator::getId);

    if (validatorId.isEmpty()) {
      return false;
    } else {
      final long id = validatorId.get();
      return jdbi.withExtension(
          SignedBlocksDao.class,
          dao -> {
            final long slot = blockSlot.toLong();
            final Optional<SignedBlock> existingBlock = dao.findExistingBlock(id, slot);
            final boolean isValid = checkSignedBlock(signingRoot, existingBlock);
            if (isValid) {
              dao.insertBlockProposal(id, slot, signingRoot);
            }
            return isValid;
          });
    }
  }

  private boolean checkSignedBlock(
      final Bytes signingRoot, final Optional<SignedBlock> signedBlock) {
    // same slot and signing_root is allowed for broadcasting previously signed block
    // otherwise if slot and different signing_root then this is a double block proposal
    return signedBlock.map(block -> block.getSigningRoot().equals(signingRoot)).orElse(true);
  }

  @Override
  public void registerValidators(final List<Bytes> validators) {
    jdbi.useExtension(ValidatorsDao.class, dao -> dao.registerMissingValidators(validators));
  }
}
