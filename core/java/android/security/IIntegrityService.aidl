package android.security;

/**
 * Binder interface to communicate with IntegrityService.
 * @hide
 */
interface IIntegrityService {
    String generateToken(String req);
}
