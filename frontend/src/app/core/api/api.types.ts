// Mirrors the backend records. Keep field names identical to the JSON.

/** identity.UserType */
export type UserType = 'CITIZEN' | 'STAFF' | 'SYSTEM';

/** identity.CurrentUser, returned by /auth/me and both login endpoints. */
export interface CurrentUser {
  id: string;
  type: UserType;
}

/** AuthController.StaffLoginRequest */
export interface StaffLoginRequest {
  email: string;
  password: string;
}
