/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The {@link AdvertisingSetParameters} provide a way to adjust advertising
 * preferences for each
 * Bluetooth LE advertising set. Use {@link AdvertisingSetParameters.Builder} to
 * create an
 * instance of this class.
 */
public final class AdvertisingSetParameters implements Parcelable {

  /**
   * Use LE_1M as both primary and secondary advertising channel.
   */
  public static final int PHY_MODE_LE_1M = 1;

  /**
   * Use LE_1M as primary and LE_2M as secondary advertising channel.
   */
  public static final int PHY_MODE_LE_2M = 2;

  /**
   * Use LE_1M as primary and LE_CODED as secondary advertising channel.
   */
  public static final int PHY_MODE_LE_CODED = 3;

  /**
   * Use LE_CODED as both primary and secondary advertising channel.
   */
  public static final int PHY_MODE_LE_CODED_ONLY = 4;

  /**
  * Advertise on low frequency. This is the default and preferred
  * advertising mode as it consumes the least power.
  */
  public static final int INTERVAL_LOW = 0;

  /**
   * Advertise on medium frequency. This is balanced between advertising
   * frequency and power consumption.
   */
  public static final int INTERVAL_MEDIUM = 1;

  /**
   * Perform high frequency, low latency advertising. This has the highest power
   * consumption and should not be used for continuous background advertising.
   */
  public static final int INTERVAL_HIGH = 2;

  /**
   * Advertise using the lowest transmission (TX) power level. Low transmission
   * power can be used
   * to restrict the visibility range of advertising packets.
   */
  public static final int TX_POWER_ULTRA_LOW = 0;

  /**
   * Advertise using low TX power level.
   */
  public static final int TX_POWER_LOW = 1;

  /**
   * Advertise using medium TX power level.
   */
  public static final int TX_POWER_MEDIUM = 2;

  /**
   * Advertise using high TX power level. This corresponds to largest visibility
   * range of the
   * advertising packet.
   */
  public static final int TX_POWER_HIGH = 3;

  /**
   * The maximum limited advertisement duration as specified by the Bluetooth
   * SIG
   */
  private static final int LIMITED_ADVERTISING_MAX_MILLIS = 180 * 1000;

  private final boolean isLegacy;
  private final boolean isAnonymous;
  private final boolean includeTxPower;
  private final int phyMode;
  private final boolean connectable;
  private final int interval;
  private final int txPowerLevel;
  private final int timeoutMillis;

  private AdvertisingSetParameters(boolean connectable, boolean isLegacy,
                                   boolean isAnonymous, boolean includeTxPower,
                                   int phyMode, int interval, int txPowerLevel,
                                   int timeoutMillis) {
    this.connectable = connectable;
    this.isLegacy = isLegacy;
    this.isAnonymous = isAnonymous;
    this.includeTxPower = includeTxPower;
    this.phyMode = phyMode;
    this.interval = interval;
    this.txPowerLevel = txPowerLevel;
    this.timeoutMillis = timeoutMillis;
  }

  private AdvertisingSetParameters(Parcel in) {
    connectable = in.readInt() != 0 ? true : false;
    isLegacy = in.readInt() != 0 ? true : false;
    isAnonymous = in.readInt() != 0 ? true : false;
    includeTxPower = in.readInt() != 0 ? true : false;
    phyMode = in.readInt();
    interval = in.readInt();
    txPowerLevel = in.readInt();
    timeoutMillis = in.readInt();
  }

  /**
   * Returns whether the advertisement will be connectable.
   */
  public boolean isConnectable() { return connectable; }

  /**
   * Returns whether the legacy advertisement will be used.
   */
  public boolean isLegacy() { return isLegacy; }

  /**
   * Returns whether the advertisement will be anonymous.
   */
  public boolean isAnonymous() { return isAnonymous; }

  /**
   * Returns whether the TX Power will be included.
   */
  public boolean includeTxPower() { return includeTxPower; }

  /**
   * Returns the advertising phy mode.
   */
  public int getPhyMode() { return phyMode; }

  /**
   * Returns the advertising interval.
   */
  public int getInterval() { return interval; }

  /**
   * Returns the TX power level for advertising.
   */
  public int getTxPowerLevel() { return txPowerLevel; }

  /**
   * Returns the advertising time limit in milliseconds.
   */
  public int getTimeout() { return timeoutMillis; }

  // @Override
  // public String toString() {
  //     return ...;
  // }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(connectable ? 1 : 0);
    dest.writeInt(isLegacy ? 1 : 0);
    dest.writeInt(isAnonymous ? 1 : 0);
    dest.writeInt(includeTxPower ? 1 : 0);
    dest.writeInt(phyMode);
    dest.writeInt(interval);
    dest.writeInt(txPowerLevel);
    dest.writeInt(timeoutMillis);
  }

  public static final Parcelable.Creator<AdvertisingSetParameters> CREATOR =
      new Creator<AdvertisingSetParameters>() {
        @Override
        public AdvertisingSetParameters[] newArray(int size) {
          return new AdvertisingSetParameters[size];
        }

        @Override
        public AdvertisingSetParameters createFromParcel(Parcel in) {
          return new AdvertisingSetParameters(in);
        }
      };

  /**
   * Builder class for {@link AdvertisingSetParameters}.
   */
  public static final class Builder {

    private boolean connectable = true;
    private boolean isLegacy = false;
    private boolean isAnonymous = false;
    private boolean includeTxPower = false;
    private int phyMode = PHY_MODE_LE_1M;
    private int interval = INTERVAL_LOW;
    private int txPowerLevel = TX_POWER_MEDIUM;
    private int timeoutMillis = 0;

    /**
     * Set whether the advertisement type should be connectable or
     * non-connectable.
     * Legacy advertisements can be both connectable and scannable. Other
     * advertisements
     * can be connectable only if not scannable.
     * @param connectable Controls whether the advertisment type will be
     * connectable (true)
     *                    or non-connectable (false).
     */
    public Builder setConnectable(boolean connectable) {
      this.connectable = connectable;
      return this;
    }

    /**
     * When set to true, advertising set will advertise 4.x Spec compliant
     * advertisements.
     *
     * @param isLegacy wether legacy advertising mode should be used.
     */
    public Builder setLegacyMode(boolean isLegacy) {
      this.isLegacy = isLegacy;
      return this;
    }

    /**
     * Set wether advertiser address should be ommited from all packets.
     *
     * This is used only if legacy mode is not used.
     *
     * @param isAnonymous wether anonymous advertising should be used
     */
    public Builder setAnonymouus(boolean isAnonymous) {
      this.isAnonymous = isAnonymous;
      return this;
    }

    /**
     * Set wether TX power should be included in the extended header.
     *
     * This is used only if legacy mode is not used.
     *
     * @param includeTxPower wether TX power should be included in extended
     * header
     */
    public Builder setIncludeTxPower(boolean includeTxPower) {
      this.includeTxPower = includeTxPower;
      return this;
    }

    /**
     * Set phy mode used for this advertising set.
     *
     * This is used only if legacy mode is not used.
     *
     * @param phyMode Bluetooth LE Advertising phy mode, can only be one of
     *            {@link AdvertisingSetParameters#PHY_MODE_LE_1M},
     *            {@link AdvertisingSetParameters#PHY_MODE_LE_2M},
     *            {@link AdvertisingSetParameters#PHY_MODE_LE_CODED}, or
     *            {@link AdvertisingSetParameters#PHY_MODE_LE_CODED_ONLY}.
     * @throws IllegalArgumentException If the phyMode is invalid.
     */
    public Builder setPhyMode(int phyMode) {
      if (phyMode < PHY_MODE_LE_1M || phyMode > PHY_MODE_LE_CODED_ONLY) {
        throw new IllegalArgumentException("unknown phyMode " + phyMode);
      }
      this.phyMode = phyMode;
      return this;
    }

    /**
     * Set advertising interval.
     *
     * @param interval Bluetooth LE Advertising interval, can only be one of
     *            {@link AdvertisingSetParameters#INTERVAL_LOW},
     *            {@link AdvertisingSetParameters#INTERVAL_MEDIUM}, or
     *            {@link AdvertisingSetParameters#INTERVAL_HIGH}.
     * @throws IllegalArgumentException If the interval is invalid.
     */
    public Builder setInterval(int interval) {
      if (interval < INTERVAL_LOW || interval > INTERVAL_HIGH) {
        throw new IllegalArgumentException("unknown interval " + interval);
      }
      this.interval = interval;
      return this;
    }

    /**
     * Set the transmission power level for the advertising.
     *
     * @param txPowerLevel Transmission power of Bluetooth LE Advertising, can
     * only be one of
     *            {@link AdvertisingSetParameters#TX_POWER_ULTRA_LOW},
     *            {@link AdvertisingSetParameters#TX_POWER_LOW},
     *            {@link AdvertisingSetParameters#TX_POWER_MEDIUM} or
     *            {@link AdvertisingSetParameters#TX_POWER_HIGH}.
     * @throws IllegalArgumentException If the {@code txPowerLevel} is invalid.
     */
    public Builder setTxPowerLevel(int txPowerLevel) {
      if (txPowerLevel < TX_POWER_ULTRA_LOW || txPowerLevel > TX_POWER_HIGH) {
        throw new IllegalArgumentException("unknown txPowerLevel " +
                                           txPowerLevel);
      }
      this.txPowerLevel = txPowerLevel;
      return this;
    }

    /**
     * Limit advertising to a given amount of time.
     * @param timeoutMillis Advertising time limit. May not exceed 180000
     * milliseconds.
     *                       A value of 0 will disable the time limit.
     * @throws IllegalArgumentException If the provided timeout is over 180000
     * ms.
     */
    public Builder setTimeout(int timeoutMillis) {
      if (timeoutMillis < 0 || timeoutMillis > LIMITED_ADVERTISING_MAX_MILLIS) {
        throw new IllegalArgumentException("timeoutMillis invalid (must be 0-" +
                                           LIMITED_ADVERTISING_MAX_MILLIS +
                                           " milliseconds)");
      }
      this.timeoutMillis = timeoutMillis;
      return this;
    }

    /**
     * Build the {@link AdvertisingSetParameters} object.
     */
    public AdvertisingSetParameters build() {
      return new AdvertisingSetParameters(connectable, isLegacy, isAnonymous,
                                          includeTxPower, phyMode, interval,
                                          txPowerLevel, timeoutMillis);
    }
  }
}