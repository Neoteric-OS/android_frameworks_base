// Copyright 2024, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! TODO

use binder::{Result, Strong};

use android_os_permissions_aidl::aidl::android::os::IPermissionController::IPermissionController;
use packagemanager_aidl::aidl::android::content::pm::{
    IPackageManagerNative::IPackageManagerNative, IStagedApexObserver::IStagedApexObserver,
    PackageInfo::PackageInfo, SigningInfo::SigningInfo, StagedApexInfo::StagedApexInfo,
};

#[allow(dead_code)]
pub struct AccessEnforcer {
    package_manager: Option<Strong<dyn IPackageManagerNative>>,
    package_manager_factory: Box<dyn FnMut() -> Result<Strong<dyn IPackageManagerNative>>>,
    permission_controller: Option<Strong<dyn IPermissionController>>,
    permission_controller_factory: Box<dyn FnMut() -> Result<Strong<dyn IPermissionController>>>,
}

impl AccessEnforcer {
    fn _new<
        PMF: FnMut() -> Result<Strong<dyn IPackageManagerNative>> + 'static,
        PCF: FnMut() -> Result<Strong<dyn IPermissionController>> + 'static,
    >(
        package_manager_factory: PMF,
        permission_controller_factory: PCF,
    ) -> Self {
        AccessEnforcer {
            package_manager: None,
            package_manager_factory: Box::new(package_manager_factory),
            permission_controller: None,
            permission_controller_factory: Box::new(permission_controller_factory),
        }
    }

    fn get_package_manager(&mut self) -> Result<Strong<dyn IPackageManagerNative>> {
        if self.package_manager.is_none() {
            self.package_manager.replace((self.package_manager_factory)()?);
        }
        Ok(self.package_manager.as_ref().unwrap().clone())
    }

    fn get_permission_controller(&mut self) -> Result<Strong<dyn IPermissionController>> {
        if self.permission_controller.is_none() {
            self.permission_controller.replace((self.permission_controller_factory)()?);
        }
        Ok(self.permission_controller.as_ref().unwrap().clone())
    }

    fn _use_factories(&mut self) -> Result<()> {
        let pm = self.get_package_manager()?;
        let _pc = self.get_permission_controller()?;

        let _p_info = pm.getPackageInfo("", 0, 0)?;
        Ok(())
    }
}

//#[cfg(test)]
mod tests {
    use std::iter::successors;

    use crate::utils::binder_exception;

    use super::*;

    use android_os_permissions_aidl::aidl::android::os::IPermissionController::{
        BnPermissionController, MockIPermissionController,
    };
    use binder::BinderFeatures;
    use packagemanager_aidl::aidl::android::content::pm::IPackageManagerNative::{
        BnPackageManagerNative, MockIPackageManagerNative,
    };

    #[test]
    fn create_access_enforcer() -> Result<()> {
        let mock_pm_factory = || {
            let mut mock = MockIPackageManagerNative::new();
            mock.expect_getPackageInfo().returning(|_, _, _| {
                Ok(PackageInfo { signingInfo: Some(SigningInfo { apkContentSigners: vec![] }) })
            });
            mock
        };
        let mut ae = AccessEnforcer::_new(
            move || {
                Ok(BnPackageManagerNative::new_binder(mock_pm_factory(), BinderFeatures::default()))
            },
            || {
                let mock_pe = MockIPermissionController::new();
                Ok(BnPermissionController::new_binder(mock_pe, BinderFeatures::default()))
            },
        );

        ae._use_factories()
    }

    #[test]
    fn get_package_manager_fails() -> Result<()> {
        let mut ae = AccessEnforcer::_new(
            || binder_exception(binder::ExceptionCode::ILLEGAL_STATE, ""),
            || {
                let mock_pe = MockIPermissionController::new();
                Ok(BnPermissionController::new_binder(mock_pe, BinderFeatures::default()))
            },
        );

        assert!(ae._use_factories().is_err());
        Ok(())
    }

    #[test]
    fn get_package_manager_succeeds() -> Result<()> {
        let mut succeed = true;
        let pm_ctor = move || {
            if succeed {
                succeed = false;
                let mut mock = MockIPackageManagerNative::new();
                mock.expect_getPackageInfo()
                    .returning(|_, _, _| Ok(PackageInfo { signingInfo: None }));
                Ok(BnPackageManagerNative::new_binder(mock, BinderFeatures::default()))
            } else {
                binder_exception(binder::ExceptionCode::ILLEGAL_STATE, "")
            }
        };
        let pe_ctor = || {
            let mock_pe = MockIPermissionController::new();
            Ok(BnPermissionController::new_binder(mock_pe, BinderFeatures::default()))
        };
        let mut ae = AccessEnforcer::_new(pm_ctor, pe_ctor);

        ae._use_factories().is_err();

        // Second time, the closure shouldn't be called.
        ae._use_factories()
    }
}
