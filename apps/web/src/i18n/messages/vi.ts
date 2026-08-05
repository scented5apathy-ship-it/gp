import type { MessageTree } from "../index";

export const vi = {
  app: {
    title: "Genealogy Platform",
    tagline: "Gia phả ưu tiên quyền riêng tư",
  },
  nav: {
    home: "Trang chủ",
    trees: "Cây gia phả",
    people: "Nhân vật",
    sources: "Nguồn",
    dna: "DNA",
    settings: "Cài đặt",
    skipToContent: "Bỏ qua để đến nội dung chính",
  },
  home: {
    headline: "Xây dựng cây gia phả mà không đánh đổi quyền riêng tư.",
    subhead:
      "Lưu trữ chủ quyền, đồng thuận minh bạch và PWA hỗ trợ ngoại tuyến — dữ liệu của bạn thuộc về bạn.",
    ctaPrimary: "Tạo cây gia phả đầu tiên",
    ctaSecondary: "Tìm hiểu về đồng thuận",
    featuresTitle: "Bạn nhận được gì",
    featureOfflineTitle: "Shell hỗ trợ ngoại tuyến",
    featureOfflineBody: "Shell PWA giữ điều hướng, manifest và design token kể cả khi mất mạng.",
    featureI18nTitle: "Đa ngôn ngữ + RTL",
    featureI18nBody:
      "Mọi chuỗi đi qua catalogue locale. Hỗ trợ RTL sẽ hoàn thiện trong E12.3 cùng ICU.",
    featureContractsTitle: "API client kiểu tĩnh",
    featureContractsBody:
      "Sinh từ OpenAPI contracts trong `contracts/openapi/` — IDE hiểu mọi endpoint.",
  },
  errors: {
    boundaryTitle: "Đã xảy ra lỗi",
    boundaryBody:
      "Trang không thể hiển thị. Lỗi đã được ghi nhận kèm correlation id; vui lòng thử lại.",
    boundaryAction: "Tải lại trang",
    notFoundTitle: "Không tìm thấy trang",
    notFoundBody: "URL bạn yêu cầu không khớp với bất kỳ route nào đã biết.",
    notFoundAction: "Về trang chủ",
    unauthorizedTitle: "Yêu cầu đăng nhập",
    unauthorizedBody: "Trang này chỉ dành cho thành viên đã xác thực.",
    unauthorizedAction: "Đăng nhập",
  },
  loading: {
    page: "Đang tải trang…",
    section: "Đang tải…",
  },
  footer: {
    rights: "Đã đăng ký bản quyền.",
    privacy: "Chính sách quyền riêng tư",
    terms: "Điều khoản dịch vụ",
  },
} as const satisfies MessageTree;
