package android.security;

interface IIntegrityProviderService{
/**
* @param appPackageName The package name of the requesting app
* @param integrityRequest ByteArray of the integrity request.
* @param callback A callback interface to return the integrity result.
* @throws RemoteException If a remote procedure call error occurs.
*/

@SystemAPI
String provideIntegrity();
}
