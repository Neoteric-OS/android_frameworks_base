

package com.android.server;


import android.annotation.Nullable;
import android.annotation.SystemApi;

import android.content.pm.VersionedPackage;

import com.android.server.PackageWatchdog.FailureReasons;
import com.android.server.PackageWatchdog.PackageHealthObserverImpact;


/** Register instances of this interface to receive notifications on package failure.
 * @hide */
public interface PackageHealthObserver {
  /**
   * Called when health check fails for the {@code versionedPackage}.
   *
   * @param versionedPackage the package that is failing. This may be null if a native
   *                          service is crashing.
   * @param failureReason   the type of failure that is occurring.
   * @param mitigationCount the number of times mitigation has been called for this package
   *                        (including this time).
   *
   *
   * @return any one of {@link PackageHealthObserverImpact} to express the impact
   * to the user on {@link #execute}
   */
  @PackageHealthObserverImpact int onHealthCheckFailed(
      @Nullable VersionedPackage versionedPackage,
      @FailureReasons int failureReason,
      int mitigationCount);

  /**
   * Executes mitigation for {@link #onHealthCheckFailed}.
   *
   * @param versionedPackage the package that is failing. This may be null if a native
   *                          service is crashing.
   * @param failureReason   the type of failure that is occurring.
   * @param mitigationCount the number of times mitigation has been called for this package
   *                        (including this time).
   * @return {@code true} if action was executed successfully, {@code false} otherwise
   */
  boolean execute(@Nullable VersionedPackage versionedPackage,
      @FailureReasons int failureReason, int mitigationCount);


  /**
   * Called when the system server has booted several times within a window of time, defined
   * by {@link #mBootThreshold}
   *
   * @param mitigationCount the number of times mitigation has been attempted for this
   *                        boot loop (including this time).
   */
  default @PackageHealthObserverImpact int onBootLoop(int mitigationCount) {
    return PackageHealthObserverImpact.USER_IMPACT_LEVEL_0;
  }

  /**
   * Executes mitigation for {@link #onBootLoop}
   * @param mitigationCount the number of times mitigation has been attempted for this
   *                        boot loop (including this time).
   */
  default boolean executeBootLoopMitigation(int mitigationCount) {
    return false;
  }

  // TODO(b/120598832): Ensure uniqueness?
  /**
   * Identifier for the observer, should not change across device updates otherwise the
   * watchdog may drop observing packages with the old name.
   */
  String getName();

  /**
   * An observer will not be pruned if this is set, even if the observer is not explicitly
   * monitoring any packages.
   */
  default boolean isPersistent() {
    return false;
  }

  /**
   * Returns {@code true} if this observer wishes to observe the given package, {@code false}
   * otherwise
   *
   * <p> A persistent observer may choose to start observing certain failing packages, even if
   * it has not explicitly asked to watch the package with {@link #startObservingHealth}.
   */
  default boolean mayObservePackage(String packageName) {
    return false;
  }
}