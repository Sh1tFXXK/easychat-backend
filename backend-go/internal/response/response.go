package response

// Response is the standard JSON envelope used by all Go backend handlers.
// It mirrors the success/message/code/data shape of the Java ResponseBO.
type Response struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Code    int         `json:"code"`
	Data    interface{} `json:"data,omitempty"`
}

const (
	CodeSuccess   = 200
	CodeBadReq    = 400
	CodeNotFound  = 404
	CodeServerErr = 500
)

// Success returns a successful response carrying data.
func Success(data interface{}) Response {
	return Response{Success: true, Message: "ok", Code: CodeSuccess, Data: data}
}

// SuccessMsg returns a successful response with a custom message and data.
func SuccessMsg(message string, data interface{}) Response {
	return Response{Success: true, Message: message, Code: CodeSuccess, Data: data}
}

// SuccessNoData returns a successful response with a message but no data.
func SuccessNoData(message string) Response {
	return Response{Success: true, Message: message, Code: CodeSuccess, Data: nil}
}

// Error returns an error response with the given message.
func Error(message string) Response {
	return Response{Success: false, Message: message, Code: CodeBadReq, Data: nil}
}

// ErrorWithCode returns an error response with a custom HTTP/application code.
// 注意 Code 必须用传入的 code —— 之前这里误写成 CodeSuccess，
// 导致 401/404/501 的响应体里 code 永远是 200，前端只能靠 HTTP 状态码判断。
func ErrorWithCode(message string, code int) Response {
	return Response{Success: false, Message: message, Code: code, Data: nil}
}
