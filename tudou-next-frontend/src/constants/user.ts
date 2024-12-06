import ACCESS_ENUM from "@/access/accessEnum";

// 默认用户
export const DEFAULT_USER: API.LoginUserVO = {
  userName: "未登录",
  userProfile: "暂无简介",
  userAvatar: "/assets/566c88e4-ee4b-462a-878d-4a3de08ed24f _63334_wantuju.jpg",
  userRole: ACCESS_ENUM.NOT_LOGIN,
};
