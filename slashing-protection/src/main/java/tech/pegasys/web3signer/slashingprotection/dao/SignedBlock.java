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
package tech.pegasys.web3signer.slashingprotection.dao;

import org.apache.tuweni.bytes.Bytes;

public class SignedBlock {
  private long validatorId;
  private long slot;
  private Bytes signingRoot;

  // needed for JDBI bean mapping
  public SignedBlock() {}

  public SignedBlock(final long validatorId, final long slot, final Bytes signingRoot) {
    this.validatorId = validatorId;
    this.slot = slot;
    this.signingRoot = signingRoot;
  }

  public long getValidatorId() {
    return validatorId;
  }

  public void setValidatorId(final long validatorId) {
    this.validatorId = validatorId;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(final long slot) {
    this.slot = slot;
  }

  public Bytes getSigningRoot() {
    return signingRoot;
  }

  public void setSigningRoot(final Bytes signingRoot) {
    this.signingRoot = signingRoot;
  }
}
