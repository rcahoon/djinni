/**
  * Copyright 2021 Snap, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

#pragma once

#include <memory>
#include <tuple>
#include <type_traits>
#include <utility>

#include "djinni_common.hpp"

namespace djinni {
namespace internal {

template <typename Ptr>
using PointeeType = std::remove_reference_t<decltype(*std::declval<Ptr>())>;

template <typename Ptr, typename Void = void>
struct IsGeneralizedPointer : std::false_type {};

template <typename Ptr>
struct IsGeneralizedPointer<Ptr, std::void_t<PointeeType<Ptr>>>
    : std::is_constructible<::djinni::SharedPtr<PointeeType<Ptr>>, Ptr> {};

template <typename Ptr, typename Void = void>
struct IsSharedPtr : std::false_type {};

template <typename Ptr>
struct IsSharedPtr<Ptr, std::void_t<PointeeType<Ptr>>>
    : std::is_same<::djinni::SharedPtr<PointeeType<Ptr>>, std::decay_t<Ptr>> {};

static_assert(std::is_same<PointeeType<std::shared_ptr<double>>, double>::value);
static_assert(std::is_same<PointeeType<std::unique_ptr<double>>, double>::value);
static_assert(std::is_same<PointeeType<double*>, double>::value);
static_assert(IsGeneralizedPointer<std::shared_ptr<double>>::value);
static_assert(IsGeneralizedPointer<std::unique_ptr<double>>::value);
static_assert(!IsGeneralizedPointer<double>::value);
static_assert(IsGeneralizedPointer<double*>::value);

template <
    typename ReturnType,
    template <typename> typename PointerPolicy,
    template <typename> typename LvaluePolicy = PointerPolicy,
    typename Function>
ReturnType valuePolicy(Function&& function) {
    auto&& result = function();
    if constexpr (std::is_lvalue_reference<decltype(result)>::value) {
        static_assert(IsSharedPtr<ReturnType>::value);
        return LvaluePolicy<ReturnType>::apply(&result);
    } else if constexpr (IsGeneralizedPointer<decltype(result)>::value) {
        static_assert(IsSharedPtr<ReturnType>::value);
        return PointerPolicy<ReturnType>::apply(std::forward<decltype(result)>(result));
    } else {
        // RValue references and return-by-value
        if constexpr (IsSharedPtr<ReturnType>::value) {
            return djinni::makeShared<ReturnType>(std::forward<decltype(result)>(result));
        } else {
            return result; // NRVO
        }
    }
}

} // namespace internal

template <typename ReturnType>
struct TakeReference {
    template <typename Pointer>
    static ReturnType apply(Pointer&& pointer) {
        return ReturnType(std::move(pointer));
    }
};

template <typename ReturnType>
struct CopyReference {
    template <typename Pointer>
    static ReturnType apply(Pointer&& pointer) {
        return djinni::makeShared<internal::PointeeType<ReturnType>>(*pointer);
    }
};

template <typename ReturnType>
struct MoveReference {
    template <typename Pointer>
    static ReturnType apply(Pointer&& pointer) {
        return djinni::makeShared<internal::PointeeType<ReturnType>>(std::move(*pointer));
    }
};

template <typename ReturnType>
struct DisconnectReference {
    template <typename Pointer>
    static ReturnType apply(Pointer&& pointer) {
        return ::djinni::SharedPtr<internal::PointeeType<ReturnType>>(&*pointer, [](void*) {});
    }
};

struct VoidPlaceholder {};
/*template <typename T>
decltype(auto) operator,(T&& lhs, VoidPlaceholder) {
    return std::forward<T>(lhs);
}*/

namespace return_value_policy {

template <typename ReturnType, typename Function>
VoidPlaceholder Void(Function&& function) {
    static_assert(
        std::is_void<ReturnType>::value,
        "Void return policy expects the function interface to return `void`");
    static_assert(
        std::is_void<decltype(function())>::value,
        "Void return policy expects the function implementation to return `void`. Use `discard_return_value` if this is intentional.");
    function();
    return VoidPlaceholder{};
}

template <typename ReturnType, typename Function>
VoidPlaceholder Discard(Function&& function) {
    static_assert(
        std::is_void<ReturnType>::value,
        "Discard return policy expects the function interface to return `void`");
    static_cast<void>(function());
    return VoidPlaceholder{};
}

template <typename ReturnType, typename Function>
ReturnType Take(Function&& function) {
    static_assert(
        internal::IsGeneralizedPointer<decltype(function())>::value,
        "This return value policy can only be applied to functions that return a pointer");
    return internal::valuePolicy<ReturnType, TakeReference>(std::forward<Function>(function));
}

template <typename ReturnType, typename Function>
ReturnType Copy(Function&& function) {
    return internal::valuePolicy<ReturnType, CopyReference>(std::forward<Function>(function));
}

template <typename ReturnType, typename Function>
ReturnType Move(Function&& function) {
    return internal::valuePolicy<ReturnType, MoveReference>(std::forward<Function>(function));
}

template <typename ReturnType, typename Function>
ReturnType Disconnect(Function&& function) {
    return internal::valuePolicy<ReturnType, DisconnectReference>(std::forward<Function>(function));
}

template <typename ReturnType, typename Function>
ReturnType Automatic(Function&& function) {
    return internal::valuePolicy<ReturnType, TakeReference, CopyReference>(
        std::forward<Function>(function));
}

} // namespace return_value_policy

template <size_t nurse_idx, size_t patient_idx, typename... Args>
void keepAlive(std::tuple<Args...>& args) {
    auto&& nurse = std::get<nurse_idx>(args);
    auto&& patient = std::get<patient_idx>(args);
    static_assert(
        internal::IsSharedPtr<decltype(nurse)>::value,
        "`Nurse` argument of keep_alive must be a reference type");
    static_assert(
        internal::IsSharedPtr<decltype(patient)>::value,
        "`Patient` argument of keep_alive must be a reference type");
    nurse.keepAlive(patient);
}

} // namespace djinni