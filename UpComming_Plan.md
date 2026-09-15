# KẾ HOẠCH TRIỂN KHAI CHI TIẾT (FINAL PHASE)

> **Mục tiêu:** Hoàn thiện dứt điểm toàn bộ tính năng cốt lõi của LMS trong 24h tới.
> **Tiêu chí (Best Practice):** Sử dụng thư viện mã nguồn mở miễn phí, giải pháp Native không phụ thuộc bên thứ ba (không tốn phí API), thuật toán tự build để duy trì vĩnh viễn.

---

## TASK 1: Hoàn thiện màn hình Tiến độ học tập (Progress Tracking)
chưa thật sự thấy effect để test

## TASK 2: Cấp quyền CRUD Học liệu cá nhân cho Học viên
CRUD CHO HỌC VIÊN CÓ NGHĨA LÀ CHỈ DÀNH RIÊNG CHO TÀI LIỆU TRONG KHO HỌC LIỆU CÁ NHÂN, TÁCH BIEJT HOÀN TOÀN VỚI TOÀN BỘ HỌC LIỆU TRONG KHO HỌC LIỆU OFFICIAL DO GIÁO VIÊN PUBLIC XUỐNG

FLASHCARD: thuật toán srs đã hoạt động, tab study chỉ để hiển thị, tap brows mới cho phép edit, chỉnh sửa card và sẽ efect ngay lặp tức chứ không phải đợi load lại cả trình duyệt mới thấy sự thay đổi, và hiện tại chưa có option xóa flashcard, đồng thời phải có option export flashcard ra thành tài liệu để có thể import vào nền tảng học thuật khác như Anki hoặc quizlet chẳng hạn, sinh viên thích có cảm giác sở hữu và việc có thể export ra để sữu hữu và dùng cho mục đích cá nhân là cần thiết, kể cả flashcard ở mục cá nhân hay là flascard ở trong kho học liệu official, có nghĩa là flashcard ở kho học liệu official được giáo viên cung cấp ngoài dùng để ôn tập ra thì cũng có nút để export ra tài nguyên để sở hữu, bổ sung một điểm nữa là flashcard sau khi được public thì bộ flash card đó bên phía học viên cũng cần phải có thuật toán srs, thuật toán srs đã hoạt động bên phía kho tài liệu cá nhân của học viên, giờ chỉ cần apply nó cho bên flashcard của kho tài liệu official thôi, khác ở chỗ là kho học liệu cá nhân thì học vien có thể thêm sửa xóa, export thành dạng tài liệu để import vào nên tảng khác như anki, còn flashcard của khóa kho học liệu official thì chỉ đơn giản là có thuật toán srs để hoặc viên ôn tập và có thêm nút export sang tài liệu để import vào nền tảng khác thôi, hiểu chứ 

mindmap: crud ở mindmap chỉ đơn giản là xoay theo 4 hướng mũi tên, tải ảnh xuống và copy code mermaid để paste sang nên tảng khác, nhưng giao diện vẫn chỉ đang hiện copy code chứ không cho biết là code đó là code gì( mermaid hay UML), cần ghi chú rõ, ghi chú ở đây có thể là hover vào nút thì thông báo sẽ hiện lên không nhất thiết phải quăng mọi thứ lên giao diện nhìn rất khó chịu

trắc nghiệm: hiện tại cũng chỉ có thẻ làm trắc nghiệm và hỏi AI gia sư về câu trả lời sai thôi, không chỉnh sửa câu hỏi câu trả lời được, không xóa câu nào đó được

phía giáo viên:
tạo học liệu
khi tạo học liệu ở mục chọn ngôn ngữ, nếu không chủ động chọn thì sẽ mặc định khong chọn ngôn ngữ nào cả dẫn đến khi nhân sinh học liệu sẽ báo lỗi không thể tạo học liệu cho ngôn ngữ này trong khi đó luồng dubbing đã mặc định khi ta up video lên thì bản thân video đó và có sub gốc đúng theo ngôn ngữ của video rồi, ít nhất thì phải luôn chọn mặc định một ngôn ngữ nào đó chứ đâu thể để trống là chọn ngôn ngữ được 
khi nhấn vào nút tạo học liệu thì màn hình hiển thị 3 loại học liệu xuất hiện 
Kho Học Liệu & Đề Thi Official
Quản lý, phát hành và kiểm duyệt toàn bộ Quiz bài thi, Bộ Flashcard và Sơ đồ Mindmap AI cho học viên.

Khóa học:

Unity
Tạo Học Liệu AI Tự Động
Lựa chọn loại học liệu bạn muốn AI tự động tổng hợp từ nội dung bài giảng.

📝
Bài Thi Trắc Nghiệm
Sinh câu hỏi trắc nghiệm kèm giải thích
🃏
Thẻ Flashcard
Trích xuất thuật ngữ & khái niệm 2 mặt
🧠
Sơ Đồ Tư Duy
Vẽ sơ đồ luồng kiến thức trực quan

và khong thể thoát ra khi nhấn vào menu bên trái mà chỉ có thể nhấn vào mũi  tên back của chính trình duyệt hoặc nhấn vào tab khác ở menu bên trái rồi nhấn lại tab Kho học liệu & đề thi hay nói cách khác breakscrum của phía trang quản lý của giáo vien chưa hoạt động, same với giao diện admin 

bài thi trắc nghiệm: hiện tại chỉ có thể xóa, sửa câu hỏi câu trả lời của câu nào đó có sẳn,chưa có nút thêm câu hỏi mới, chưa có option để tạo câu hỏi dạng cho chọn nhiều đáp án, tạo tự động bằng AI hầu như chỉ có thể tạo dạng câu hỏi với một câu trả lời duy nhất, điều đó vẫn ổn, nhưng phải thêm đầy đủ tính năng cho phần thêm câu hỏi mới để có thể custome loại câu hỏi miows được thêm là dạng chọn 1 đáp án đúng hay là dạng có thể chọn nhiều câu trả lời-> từ đây kéo theo việc tính điểm bài thi cũng phải xem lại, thì điểm của một câu có thể bị chia nhỏ ra nữa dựa vào số câu trả lời đúng trong dạng câu hỏi cho chọn nhiều câu  trả lời như thế này 

flashcard hiện tại chỉ hiển thị chứ chưa hề có bất kì thao tác thêm card, sửa nội dung card hay xóa card gì cả, và flash card sau khi được đánh dấu là official để hiển thị bên tab kho học liệu offiial bên phía học viên

sơ đồ tư duy đang cố tái hiện nền tảng xmind vào đê giảng viên có thể chọn nheieuf template(như fishbone, matrix, tree,...), theme color tùy ý chỉnh sửa font chữ, size, border, vị trí node nhưng hiện tại chỉ có thể, thêm xóa sửa node hoặc nhánh, chọn các theme màu cố định, chỉnh sửa nội dung node, màu chữ, màu nền node một cách cơ bản thôi và hiện ở tab xem tĩnh vẫn tồn tại 4 dấu mũi tên và tôi muốn loại bỏ hoàn toàn do hiện trong phần layout của tap chỉnh sửa đã có option cho phép xoay 4 hướng rồi, tap tĩnh chỉ để hiển thị output cuối cùng mà chính phía học viên cũng sẽ nhìn thấy và để copy mã mermaid thôi
## TASK 3: Tối ưu luồng làm bài Quiz Public (Official)

đém ngược thời gian và dạng ma trận 2 chiều hiển thị toàn bộ câu hỏi dạng
1 2 3 4 5
6 7 9 9 10
........
chẳng hạn phải nằm BÊN DƯỚI, nhắc lại là BÊN DƯỚI ô hiển thị avt giám sát và phải sticky cố định không bị  ẩn mất khi người dùng scroll để làm các câu phía dưới, thực hiện phân trang tối đa 5 câu hỏi trên một trang, cấu hình mũi tên < > cho phép nhảy đến trang tiếp theo hoặc nhảy về trang trước đó
cá số trong ma trận 2 nheieuf biểu thị cho câu hỏi đã được làm rồi (ô vuông chia theo tỉ lệ 70 phía trên để hiển thị câu hỏi thứ mấy và 30% phía dưới để hiển thị dấu tích nền xanh đổi với câu hỏi làm rồi và dấu x nền đỏ đối với câu hỏi chưa làm), và khi nhấn vào câu nào thì màn hình sẽ forward về đúng câu đó cho dù câu đó có ở trang nào đi chăng nữa , toàn bộ ma trận hai chiều này cũng phải hiển thiij trong màn hình xem chi tiết bài làm hay màn hình lịch sử, chứ nếu không giả sử bài làm 200 câu mà chỉ có ma trận xuất hiện trong màn hình khi làm bài thi thôi thì khi xem lại bài thi thì phải cuộn màn hình trong suốt 200 trang đó chỉ để kiếm một câu nào đó à

phải kiểm tra thật kĩ việc sinh viên có hoàn thành hết cau hỏi chưa, đã chọn đáp án hết chưa, và phải có bước confirm trước khi chính thức nộp bài, khi nhấn nộp bài, một màn hình hiển thị từng dòng thể hiện cho từng câu hỏi với dòng chữ: Đã ghi nhận đáp án, và sinh viên phải kéo xuống tận phía cuối đến câu hỏi cuối cùng mới có nút Xác nhận nộp bài thì lúc đó mới có thể tiến hành nộp, ta không thiết kế dạng ma trận 2 chiều hay dạng rút gọn nào hết do đây là chốt chặn cuối cùng để bắt chính sinh viên kiểm tra lại xem mình có bỏ xót câu nào (sẽ hiển thị là Chưa ghi nhận câu trả lời) không, tóm lại là có 2 chốt chặn cho việc nộp bài, lần nhán nút nộp bài đầu tiên, kiểm tra thẳng trên giao diện: có câu chưa làm thì thông báo lên màn hình: Bạn có câu hỏi chưa trả lời hoặc có câu hỏi chưa làm hay gì đó tương tự vậy, chốt chặn thứ hai là màn hình confirm để review lại hết những câu mình đã làm xem coi có câu nào hiển thị là Chưa ghi nhận đáp án hay không, màn hình đó phía dưới cuối sẽ có nút Xác nhận nộp bài hoặc Quay lại bài thi.

---



---

## TASK 10: Quản lý thiết bị / Phiên làm việc đa nền tảng
**Mức độ:** Trung bình | **Phạm vi:** Backend

một tài khoản đăng nhập trên máy tính này thì duy trì đăng nhập được bao lâu, khi ta đến máy tính công cộng hay dùng thiết bị khác đăng nhập vào tài khoản thì ngoài các bước xác minh 2FA thì khi đăng nhập vào được rồi thi tài khoản ở máy kia có bị cưỡng chế đăng xuất ra không

tương tự với điện thoại, ipad,....

có cơ chế lưu lại trạng thái đăng nhập giống các trang web hiện đại không: ví dụ youtube, bạn tải lần đâu thì đăng nhập bằng google, nhưng khi bạn dùng nhiều thì chỉ cần đơn giản gõ chữ y trên thanh tìm kiếm là hiện sẳn url và khi nhấn vào bạn vào thẳng trang chủ đã đăng nhập sẳn của mình chứ không bị bắt phải đăng nhập lại nữa hay gặp tình trạng refresh token bị hết hạn và phải đăng xuát thủ công ra và đăng nhập lại

## TASK 11: Hoàn thiện Quản lý User (Admin, Instructor, Student)
admin quản lý tài khoản thế nào, quản lý quyền read, write như nào ,quản lý khóa học như nào, quản lý contributor của khóa học đó như nào , quản lý owner của khóa học đó như nào, quản lý được các thông số, tài nguyên nào của người dùng, của khóa học

có các hành động nào mang tính mạnh tay: đánh dấu tài khoản là spam, ham? dẫn đến cưỡng chế logout và không cho đăng nhập lại, quản lý khôi phục tài khoản, gửi mail cấp lại mật khẩu,....

quản lý khi nội dung đồ sộ từ hàng ngàn giáo viên và hàng chục ngàn video như nào,có công cụ hổ trợ nào để hổ trợ kiểm duyệt video, nhở đâu ai đó đăng video 18+ hoặc mang xu hướng bạo lực thì sao, có các cơ chế hành động tức  thì một cách tự động nào ví dụ một user spam đánh giá ảo đến một khóa học của một giáo viên, giáo viên đánh dấu người dùng đó là spam và report sẽ xuất hiện bên phía admin thì admin sẽ review thế nào và sẽ có các hành động nào để lựa chọn
=============

instroctor quản lý tài khoản học viên trong nội bộ khóa học của mình như thế nào, có khác hành động nào, chứ khong thể đơn giản là giảng viên tạo khóa học, up video lên, bán nó, học viên mua rồi xem video thụ động, mỗi phía làm việc riêng của họ, không có sự liên quan ảnh hưởng nào, instructor quản lý đánh giá, đánh giá ảo, đánh giá mang tính spam, quản lý hành vi luồng học viên thế nào, quản lý ghi danh học viên thế nào, quản lý hiệu xuất các video trong khóa học thế nào, có các recomment nào dựa vào xu hướng phản hồi của người dùng từ video hay khóa học nào để để tự dodonggj đề xuất cải thiện????, trả lời đánh giá của học viên được không, có thể ẩn hay xóa hay báo cáo gì đó đối với các comment mang tính công kích không, trong phiên live thì có công cụ nào để tự động theo giỏi luồng bình luận để nhận biết spam và tự động gửi báo cáo về hệ thống để admin xem sét không và sẽ tự động ẩn user đó ra khỏi bình luận và kick ra khỏi phiên live không

==========================
user:
có các hành động nào đối với chính tài khoản của mình, cụ thể là trong tab trang có nhân, có xác thực 2FA không, có liên kết được với Authencicator để tăng cường tính báo mật không, có edit trang cá nhân như đổi avt, đỏi tên hiển thị, thay đổi mật khẩu xác nhận bằng email được không, khi quên mật khẩu thì có khôi phục lại bằng email được không hay gửi yêu cầu cấp lại mật khẩu bằng email để admin nhận yêu cầu gửi lại mật khẩu và thực hiện gì đó được không
có các cài đặt nào mang tính cá nhân hóa không
====================================================

phần cần thảo luận và đưa ra hướng đi

 tính năng duyệt khóa học với duyejt yêu cầu trở thành giáo viên t nghĩ nên bỏ

 kiểu cho nó trở thành nền tảng vừa học được vừa upload khóa học được luôn, ví dụ một user thường vẫn có chỗ để tự tạo khóa học rồi gửi cho admin duyệt, trong vòng 3 5 7 ngày gì đó nếu nội dung được chấp thuận thì khóa học đó, video đó được công khai, còn không thị bị từ chối kiểm duyệt

chứ bây giờ một học viên muốn thành giáo viên thì cần phải up minh chứng, nhưng phải up, cái gì, up bao nhiêu, giống như ytb các trang dạy học học đơn giản là chiaw sẻ kiến thức thôi chứ họ cũng đâu phải kiểm duyệt cái gì, nếu có kiểm duyệt cung ở mức tài khoản khoản thôi, do đâu phải lúc nào cũng tạo khóa học với một chủ đề duy nhất đâu, có thể tạo khóa học dạy OOP, rồi sau đó tạo khóa học dạy tiếng anh nếu thấy tiếng anh ổn, thế thì phải up cái gì cho OOP và up cái gì cho khóa học tiếng anh, không hệ thống nào hoặc không admin nào có đủ khả năng để liệt kê để tạo khóa học này bạn phải nộp cái này nộp cái kia, rồi giả dụ có yêu cầu cụ thể luôn là nộp bằng ielts, cho dù user up lên cái ảnh chụp cái bằng thật, admin hay nói đúng hơn là hệ thống đâu có cơ sở pháp lý nào để xác minh bằng đó là hợp lệ đâu

nên dễ nhất là làm giống thằng ytb: mọi người dùng đều có thể trở thành giáo viên miễn là họ thỏa được một số thứ về tài khoản: xác thực 2FA rồi, tài khoản không bị đánh dấu là spam trước đây, ....., khâu kiểm duyệt nghiêm ngặt hơn sẽ nằm về phía hệ thống hay của thằng admin, khi khóa học kèm video được gửi lên hệ thống để kiểm duyệt, thì hệ thống bằng cách nào đó phải tự động kiểm duyệt được hình ảnh, âm thanh, bản quyền, bắt người dùng đợi rồi mới duyệt, tương tự cho video với mấy khóa học sau.

chứ giờ cái kiểm duyệt để trở thành giáo viên hiện tại là k dc r, do t đang chuẩn bị làm chức năng liên quan đến phân quyền, liên quan đến tài khoản, contributor, khóa học các thứ nên thấy nhiều vấn đề lắm

hệ thống đâu chỉ dừng lại ở mỗi thằng người dùng bình thường thôi đâu
