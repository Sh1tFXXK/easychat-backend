# 路由清单（自动生成 · 共 81 条）

由 `go run .` 的 GIN-debug 输出生成；`TODO 501` = 路由已挂、handler 待实现。

| 方法 | 路径 | Handler | 状态 |
|---|---|---|---|
| GET | `/health` | 匿名函数 | 已实现 |
| GET | `/healthz` | 匿名函数 | 已实现 |
| GET | `/admin/error-monitor/circuit-breaker` | `AdminHandler.GetCircuitBreakerStats` | TODO 501 |
| POST | `/admin/error-monitor/circuit-breaker/reset` | `AdminHandler.ResetCircuitBreaker` | TODO 501 |
| POST | `/admin/error-monitor/cleanup` | `AdminHandler.CleanupExpiredData` | TODO 501 |
| GET | `/admin/error-monitor/config` | `AdminHandler.GetErrorHandlingConfig` | TODO 501 |
| GET | `/admin/error-monitor/health` | `AdminHandler.GetSystemHealth` | TODO 501 |
| GET | `/admin/error-monitor/statistics` | `AdminHandler.GetErrorStatistics` | TODO 501 |
| POST | `/admin/error-monitor/test-error` | `AdminHandler.TestError` | TODO 501 |
| POST | `/api/auth/login` | `AuthHandler.Login` | TODO 501 |
| POST | `/api/auth/logout` | `AuthHandler.Logout` | TODO 501 |
| GET | `/api/chat/chats` | `ChatHandler.GetChats` | TODO 501 |
| GET | `/api/chat/chats/chatHistory` | `ChatHandler.GetChatHistory` | TODO 501 |
| POST | `/api/chat/chats/saveFileMsg` | `ChatHandler.SaveFileMsg` | TODO 501 |
| POST | `/api/chat/chats/savePictureMsg` | `ChatHandler.SavePictureMsg` | TODO 501 |
| POST | `/api/chat/chats/uploadVoice` | `ChatHandler.UploadVoice` | TODO 501 |
| GET | `/api/favorites` | `FavoriteHandler.ListFavorites` | 已实现 |
| POST | `/api/favorites` | `FavoriteHandler.AddFavorite` | 已实现 |
| DELETE | `/api/favorites/:id` | `FavoriteHandler.DeleteFavorite` | 已实现 |
| GET | `/api/favorites/:id` | `FavoriteHandler.GetFavorite` | 已实现 |
| PUT | `/api/favorites/:id` | `FavoriteHandler.UpdateFavorite` | TODO 501 |
| DELETE | `/api/favorites/batch` | `FavoriteHandler.BatchDeleteFavorites` | TODO 501 |
| PUT | `/api/favorites/batch/category` | `FavoriteHandler.BatchUpdateCategory` | TODO 501 |
| PUT | `/api/favorites/batch/tags/add` | `FavoriteHandler.BatchAddTags` | TODO 501 |
| PUT | `/api/favorites/batch/tags/remove` | `FavoriteHandler.BatchRemoveTags` | TODO 501 |
| GET | `/api/favorites/categories` | `FavoriteHandler.GetCategories` | TODO 501 |
| POST | `/api/favorites/categories` | `FavoriteHandler.CreateCategory` | TODO 501 |
| DELETE | `/api/favorites/categories/:id` | `FavoriteHandler.DeleteCategory` | TODO 501 |
| PUT | `/api/favorites/categories/:id` | `FavoriteHandler.UpdateCategory` | TODO 501 |
| POST | `/api/favorites/categories/init` | `FavoriteHandler.InitDefaultCategories` | TODO 501 |
| GET | `/api/favorites/check-duplicate` | `FavoriteHandler.CheckDuplicate` | TODO 501 |
| POST | `/api/favorites/search` | `FavoriteHandler.AdvancedSearch` | TODO 501 |
| GET | `/api/favorites/statistics` | `FavoriteHandler.GetStatistics` | TODO 501 |
| GET | `/api/favorites/tags` | `FavoriteHandler.GetTags` | TODO 501 |
| POST | `/api/favorites/tags` | `FavoriteHandler.CreateTag` | TODO 501 |
| DELETE | `/api/favorites/tags/:id` | `FavoriteHandler.DeleteTag` | TODO 501 |
| DELETE | `/api/favorites/tags/cleanup` | `FavoriteHandler.CleanupTags` | TODO 501 |
| GET | `/api/favorites/tags/popular` | `FavoriteHandler.GetPopularTags` | TODO 501 |
| POST | `/api/group/groups` | `GroupHandler.CreateGroup` | TODO 501 |
| DELETE | `/api/group/groups/:groupId` | `GroupHandler.DeleteGroup` | TODO 501 |
| GET | `/api/group/groups/:groupId` | `GroupHandler.GetGroupDetail` | TODO 501 |
| PUT | `/api/group/groups/:groupId` | `GroupHandler.UpdateGroup` | TODO 501 |
| POST | `/api/group/groups/:groupId/members` | `GroupHandler.InviteMembers` | TODO 501 |
| DELETE | `/api/group/groups/:groupId/members/:userId` | `GroupHandler.RemoveMember` | TODO 501 |
| GET | `/api/group/groups/:groupId/messages` | `GroupHandler.GetGroupMessages` | TODO 501 |
| POST | `/api/group/messages/file` | `GroupHandler.SendGroupFile` | TODO 501 |
| POST | `/api/group/messages/image` | `GroupHandler.SendGroupImage` | TODO 501 |
| POST | `/api/group/messages/text` | `GroupHandler.SendGroupText` | TODO 501 |
| GET | `/api/group/users/:userId/groups` | `GroupHandler.GetUserGroups` | TODO 501 |
| GET | `/api/messages` | `MessageHandler.ListMessages` | 已实现 |
| POST | `/api/messages` | `MessageHandler.CreateMessage` | 已实现 |
| GET | `/api/user/:id` | `UserHandler.GetUser` | 已实现 |
| GET | `/api/user/friendVerify` | `UserHandler.GetFriendVerifyList` | TODO 501 |
| GET | `/api/user/friends` | `UserHandler.GetFriendsList` | TODO 501 |
| POST | `/api/user/login` | `UserHandler.Login` | 已实现 |
| POST | `/api/user/notification/retry` | `NotificationHandler.RetryFailed` | TODO 501 |
| PUT | `/api/user/notification/settings` | `NotificationHandler.UpdateSettings` | TODO 501 |
| PUT | `/api/user/notification/template` | `NotificationHandler.SetTemplate` | TODO 501 |
| POST | `/api/user/password/validate` | `UserHandler.ValidatePassword` | TODO 501 |
| POST | `/api/user/register` | `UserHandler.Register` | 已实现 |
| GET | `/api/user/search` | `UserHandler.SearchUser` | 已实现 |
| DELETE | `/api/user/special-attention` | `SpecialAttentionHandler.RemoveAttention` | TODO 501 |
| GET | `/api/user/special-attention` | `SpecialAttentionHandler.GetAttentionList` | TODO 501 |
| POST | `/api/user/special-attention` | `SpecialAttentionHandler.AddAttention` | TODO 501 |
| PUT | `/api/user/special-attention` | `SpecialAttentionHandler.UpdateAttention` | TODO 501 |
| POST | `/api/user/special-attention/batch` | `SpecialAttentionHandler.BatchOperation` | TODO 501 |
| DELETE | `/api/user/special-attention/by-user` | `SpecialAttentionHandler.RemoveAttentionByUser` | TODO 501 |
| GET | `/api/user/special-attention/check` | `SpecialAttentionHandler.CheckAttentionStatus` | TODO 501 |
| GET | `/api/user/special-attention/updates` | `SpecialAttentionHandler.GetAttentionUpdates` | TODO 501 |
| GET | `/api/user/user` | `UserHandler.GetUser` | 已实现 |
| POST | `/api/user/user/addTag` | `UserHandler.AddTag` | TODO 501 |
| POST | `/api/user/user/changeAvatar` | `UserHandler.ChangeAvatar` | TODO 501 |
| POST | `/api/user/user/changePassword` | `UserHandler.ChangePassword` | TODO 501 |
| POST | `/api/user/user/edit` | `UserHandler.EditUser` | TODO 501 |
| POST | `/api/user/user/removeTag` | `UserHandler.RemoveTag` | TODO 501 |
| POST | `/api/user/username/validate` | `UserHandler.ValidateUsername` | 已实现 |
| POST | `/api/user/verifyCode/send` | `UserHandler.SendVerifyCode` | TODO 501 |
| POST | `/api/user/verifyCode/validate` | `UserHandler.ValidateVerifyCode` | TODO 501 |
| POST | `/auth/login` | `AuthHandler.Login` | TODO 501 |
| POST | `/auth/logout` | `AuthHandler.Logout` | TODO 501 |
| POST | `/user/notification/retry` | `NotificationHandler.RetryFailed` | TODO 501 |
| PUT | `/user/notification/settings` | `NotificationHandler.UpdateSettings` | TODO 501 |
| PUT | `/user/notification/template` | `NotificationHandler.SetTemplate` | TODO 501 |
