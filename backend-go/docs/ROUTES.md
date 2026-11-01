# 路由清单（自动生成 · 共 89 条）

由 `go run .` 的 GIN-debug 输出生成。阶段 1 全部接口 + 阶段 2 Redis 接口（未读/在线状态）。

| 方法 | 路径 | Handler |
|---|---|---|
| GET | `/admin/error-monitor/circuit-breaker` | `AdminHandler.GetCircuitBreakerStats` |
| POST | `/admin/error-monitor/circuit-breaker/reset` | `AdminHandler.ResetCircuitBreaker` |
| POST | `/admin/error-monitor/cleanup` | `AdminHandler.CleanupExpiredData` |
| GET | `/admin/error-monitor/config` | `AdminHandler.GetErrorHandlingConfig` |
| GET | `/admin/error-monitor/health` | `AdminHandler.GetSystemHealth` |
| GET | `/admin/error-monitor/statistics` | `AdminHandler.GetErrorStatistics` |
| POST | `/admin/error-monitor/test-error` | `AdminHandler.TestError` |
| POST | `/api/auth/login` | `AuthHandler.Login` |
| POST | `/api/auth/logout` | `AuthHandler.Logout` |
| GET | `/api/chat/chats` | `ChatHandler.GetChats` |
| GET | `/api/chat/chats/chatHistory` | `ChatHandler.GetChatHistory` |
| POST | `/api/chat/chats/read` | `ChatHandler.MarkRead` |
| POST | `/api/chat/chats/readAll` | `ChatHandler.MarkAllRead` |
| POST | `/api/chat/chats/saveFileMsg` | `ChatHandler.SaveFileMsg` |
| POST | `/api/chat/chats/savePictureMsg` | `ChatHandler.SavePictureMsg` |
| POST | `/api/chat/chats/uploadVoice` | `ChatHandler.UploadVoice` |
| GET | `/api/chat/unread` | `ChatHandler.GetUnread` |
| GET | `/api/favorites` | `FavoriteHandler.ListFavorites` |
| POST | `/api/favorites` | `FavoriteHandler.AddFavorite` |
| DELETE | `/api/favorites/:id` | `FavoriteHandler.DeleteFavorite` |
| GET | `/api/favorites/:id` | `FavoriteHandler.GetFavorite` |
| PUT | `/api/favorites/:id` | `FavoriteHandler.UpdateFavorite` |
| DELETE | `/api/favorites/batch` | `FavoriteHandler.BatchDeleteFavorites` |
| PUT | `/api/favorites/batch/category` | `FavoriteHandler.BatchUpdateCategory` |
| PUT | `/api/favorites/batch/tags/add` | `FavoriteHandler.BatchAddTags` |
| PUT | `/api/favorites/batch/tags/remove` | `FavoriteHandler.BatchRemoveTags` |
| GET | `/api/favorites/categories` | `FavoriteHandler.GetCategories` |
| POST | `/api/favorites/categories` | `FavoriteHandler.CreateCategory` |
| DELETE | `/api/favorites/categories/:id` | `FavoriteHandler.DeleteCategory` |
| PUT | `/api/favorites/categories/:id` | `FavoriteHandler.UpdateCategory` |
| POST | `/api/favorites/categories/init` | `FavoriteHandler.InitDefaultCategories` |
| GET | `/api/favorites/check-duplicate` | `FavoriteHandler.CheckDuplicate` |
| POST | `/api/favorites/search` | `FavoriteHandler.AdvancedSearch` |
| GET | `/api/favorites/statistics` | `FavoriteHandler.GetStatistics` |
| GET | `/api/favorites/tags` | `FavoriteHandler.GetTags` |
| POST | `/api/favorites/tags` | `FavoriteHandler.CreateTag` |
| DELETE | `/api/favorites/tags/:id` | `FavoriteHandler.DeleteTag` |
| DELETE | `/api/favorites/tags/cleanup` | `FavoriteHandler.CleanupTags` |
| GET | `/api/favorites/tags/popular` | `FavoriteHandler.GetPopularTags` |
| POST | `/api/group/groups` | `GroupHandler.CreateGroup` |
| DELETE | `/api/group/groups/:groupId` | `GroupHandler.DeleteGroup` |
| GET | `/api/group/groups/:groupId` | `GroupHandler.GetGroupDetail` |
| PUT | `/api/group/groups/:groupId` | `GroupHandler.UpdateGroup` |
| POST | `/api/group/groups/:groupId/members` | `GroupHandler.InviteMembers` |
| DELETE | `/api/group/groups/:groupId/members/:userId` | `GroupHandler.RemoveMember` |
| GET | `/api/group/groups/:groupId/messages` | `GroupHandler.GetGroupMessages` |
| POST | `/api/group/messages/file` | `GroupHandler.SendGroupFile` |
| POST | `/api/group/messages/image` | `GroupHandler.SendGroupImage` |
| POST | `/api/group/messages/text` | `GroupHandler.SendGroupText` |
| GET | `/api/group/users/:userId/groups` | `GroupHandler.GetUserGroups` |
| GET | `/api/messages` | `MessageHandler.ListMessages` |
| POST | `/api/messages` | `MessageHandler.CreateMessage` |
| POST | `/api/presence/heartbeat` | `PresenceHandler.Heartbeat` |
| POST | `/api/presence/offline` | `PresenceHandler.Offline` |
| DELETE | `/api/presence/online` | `PresenceHandler.Offline` |
| GET | `/api/presence/online` | `PresenceHandler.BatchStatus` |
| GET | `/api/presence/status` | `PresenceHandler.GetStatus` |
| GET | `/api/user/:id` | `UserHandler.GetUser` |
| GET | `/api/user/friendVerify` | `UserHandler.GetFriendVerifyList` |
| GET | `/api/user/friends` | `UserHandler.GetFriendsList` |
| POST | `/api/user/login` | `UserHandler.Login` |
| POST | `/api/user/notification/retry` | `NotificationHandler.RetryFailed` |
| PUT | `/api/user/notification/settings` | `NotificationHandler.UpdateSettings` |
| PUT | `/api/user/notification/template` | `NotificationHandler.SetTemplate` |
| POST | `/api/user/password/validate` | `UserHandler.ValidatePassword` |
| POST | `/api/user/register` | `UserHandler.Register` |
| GET | `/api/user/search` | `UserHandler.SearchUser` |
| DELETE | `/api/user/special-attention` | `SpecialAttentionHandler.RemoveAttention` |
| GET | `/api/user/special-attention` | `SpecialAttentionHandler.GetAttentionList` |
| POST | `/api/user/special-attention` | `SpecialAttentionHandler.AddAttention` |
| PUT | `/api/user/special-attention` | `SpecialAttentionHandler.UpdateAttention` |
| POST | `/api/user/special-attention/batch` | `SpecialAttentionHandler.BatchOperation` |
| DELETE | `/api/user/special-attention/by-user` | `SpecialAttentionHandler.RemoveAttentionByUser` |
| GET | `/api/user/special-attention/check` | `SpecialAttentionHandler.CheckAttentionStatus` |
| GET | `/api/user/special-attention/updates` | `SpecialAttentionHandler.GetAttentionUpdates` |
| GET | `/api/user/user` | `UserHandler.GetUser` |
| POST | `/api/user/user/addTag` | `UserHandler.AddTag` |
| POST | `/api/user/user/changeAvatar` | `UserHandler.ChangeAvatar` |
| POST | `/api/user/user/changePassword` | `UserHandler.ChangePassword` |
| POST | `/api/user/user/edit` | `UserHandler.EditUser` |
| POST | `/api/user/user/removeTag` | `UserHandler.RemoveTag` |
| POST | `/api/user/username/validate` | `UserHandler.ValidateUsername` |
| POST | `/api/user/verifyCode/send` | `UserHandler.SendVerifyCode` |
| POST | `/api/user/verifyCode/validate` | `UserHandler.ValidateVerifyCode` |
| POST | `/auth/login` | `AuthHandler.Login` |
| POST | `/auth/logout` | `AuthHandler.Logout` |
| POST | `/user/notification/retry` | `NotificationHandler.RetryFailed` |
| PUT | `/user/notification/settings` | `NotificationHandler.UpdateSettings` |
| PUT | `/user/notification/template` | `NotificationHandler.SetTemplate` |

## 健康检查

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 存活探针 |
| GET | `/healthz` | 同上 |

> 上传文件通过 `/uploads/<name>` 静态访问。
