package android.security;

interface IIntegrityService {
/**
* @param integrityRequest ByteArray of the integrity request.
* @param callback A callback interface to return the integrity result.
* @param provider provider preference by app
* @throws RemoteException If a remote procedure call error occurs.
*/
    String generateIntegrityCertificate();
}
