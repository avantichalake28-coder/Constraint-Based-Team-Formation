# Student avatar inventory

The original character collection is stored unchanged in `frontend/public/images/`.
Character IDs in the registry are stable frontend identifiers; numbered names
remain the original filenames.

| ID | Original file | Format | Dimensions | Animation |
| --- | --- | --- | --- | --- |
| `avatar-01` | `Avatar 1.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-02` | `Avatar 2.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-03` | `Avatar 3.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-04` | `Avatar 4.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-05` | `Avatar 5.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-06` | `Avatar 6.jpg` | JPEG | 1220 x 2712 | Static |
| `avatar-07` | `Avatar 7.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-08` | `Avatar 8.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-09` | `Avatar 9.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-10` | `Avatar 10.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-11` | `Avatar 11.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-12` | `Avatar 12.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-13` | `Avatar 13.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-14` | `Avatar 14.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-15` | `Avatar 15.jpg` | JPEG | 610 x 1356 | Static |
| `avatar-16` | `Avatar 16.jpg` | JPEG | 610 x 1356 | Static |

All 16 files are single-frame RGB JPEG images. The collection contains no PNG,
SVG, GIF, animated WebP, Lottie, or Rive character files. No animated states
are available. `StudentAvatar` renders the original file at reusable sizes; it
does not alter or substitute the artwork. Settings previews preserve the full
portrait aspect ratio. The welcome-screen grid uses a circular, cover-fitted
thumbnail, while its larger spotlight retains the full portrait. The two
`auth-aurora-*.png` files are login-reference artwork and are not student
avatar entries.

The settings preview and welcome-screen grid descriptions above refer to
dormant components; neither is currently reachable from the student routes.

## Selection and persistence

Avatar selection is currently paused and is not part of registration, login,
the dashboard, or the student settings routes. The existing avatar components,
assets, registry, and browser-local selection store are retained as isolated
reusable functionality for possible reintroduction. There is no active
onboarding requirement or redirect to avatar customization.

The existing backend does **not** support account-level avatar persistence:
`PUT /api/v1/students/me` accepts only `fullName`, `department`, `semester`,
and `interests`; `StudentProfileResponse` exposes no avatar field, and the
`student_profiles` entity has no avatar column. The existing selection store
is browser-local and is not used by the student application. Backend
integration requires an
`avatarId` field in a database migration, the profile entity, update request
and response DTOs, plus service/controller mapping and validation against this
registry.
