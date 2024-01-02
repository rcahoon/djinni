//
// Copyright 2015 Dropbox, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#pragma once

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <type_traits>
#include <vector>

#ifdef _MSC_VER
    #define DJINNI_WEAK_DEFINITION // weak attribute not supported by MSVC
    #define DJINNI_NORETURN_DEFINITION __declspec(noreturn)
    #if _MSC_VER < 1900 // snprintf not implemented prior to VS2015
        #define DJINNI_SNPRINTF snprintf
        #define noexcept _NOEXCEPT // work-around for missing noexcept VS2015
        #define constexpr // work-around for missing constexpr VS2015
    #else
        #define DJINNI_SNPRINTF _snprintf
    #endif
#else
    #define DJINNI_WEAK_DEFINITION __attribute__((weak))
    #define DJINNI_NORETURN_DEFINITION __attribute__((noreturn))
    #define DJINNI_SNPRINTF snprintf
#endif

namespace djinni {

template <typename T>
class WeakPtr;

//TODO: this can be optimized so that each managed object only needs one allocation
// (ignoring the allocations that may be needed by the vector, which ought to use a
// small size optimized container)
template <typename T>
class SharedPtr {
public:
    using element_type = std::remove_extent_t<T>;

    SharedPtr() = default;

    constexpr SharedPtr(std::nullptr_t) noexcept : ptr_(nullptr) {}

    template <
        typename Y,
        typename std::enable_if<
            std::is_constructible<std::shared_ptr<T>, std::shared_ptr<Y>&&>::value,
            int>::type = 0>
    SharedPtr(SharedPtr<Y> r) noexcept : ptr_(std::move(r.ptr_)) {}

    template<typename Y>
    SharedPtr(const SharedPtr<Y>& r, element_type* ptr) noexcept : ptr_(r.ptr_, ptr) {}

    explicit SharedPtr(std::shared_ptr<T> r) {
        if (r == nullptr) {
            return;
        } else if (std::get_deleter<Manager>(r)) {
            ptr_ = std::move(r);
        } else {
            T* addr = r.get();
            ptr_ = std::shared_ptr<T>{addr, Manager{{std::move(r)}}};
        }
    }

    template <
        typename... Args,
        typename std::enable_if<
            std::is_constructible<std::shared_ptr<T>, Args...>::value,
            int>::type = 0>
    explicit SharedPtr(Args&&... args)
        : SharedPtr(std::shared_ptr<T>{std::forward<Args>(args)...}) {}

    explicit SharedPtr(const WeakPtr<T>&) noexcept;

    element_type* get() const noexcept {
        return ptr_.get();
    }
    decltype(auto) operator*() const noexcept {
        return ptr_.operator*();
    }
    decltype(auto) operator->() const noexcept {
        return ptr_.operator->();
    }
    explicit operator bool() const noexcept {
        return ptr_.operator bool();
    }

    operator std::shared_ptr<T>() const {
        return ptr_;
    }

    void keepAlive(std::shared_ptr<void> patient) {
        if (ptr_ == nullptr) {
            return;
        }
        auto* manager = std::get_deleter<Manager>(ptr_);
        if (manager == nullptr) {
            std::fputs("Internal error: SharedPtr did not find the expected Manager", stderr);
            std::fflush(stderr);
            std::abort();
        }
        if (std::find(manager->patients.begin(), manager->patients.end(), patient) == manager->patients.end()) {
            manager->patients.push_back(std::move(patient));
        }
    }

private:
    friend class WeakPtr<T>;
    template <typename Y> friend class SharedPtr;
    
    struct Manager {
        std::vector<std::shared_ptr<void>> patients;

        // Called when the managed object is to be deleted.
        void operator()(T* ptr) {
            patients.clear();
        }

        /*
        template <typename T, typename Deleter = typename std::default_delete<T> >
        struct DisarmableDelete : private Deleter {
            void operator()(T* ptr) { if(_armed) Deleter::operator()(ptr); }
            bool _armed = true;
        };
        */
    };

    std::shared_ptr<T> ptr_;
};

template <typename T>
class WeakPtr {
public:
    WeakPtr() = default;
    WeakPtr(const SharedPtr<T>& r) noexcept : ptr_(r.ptr_) {}

    SharedPtr<T> lock() const noexcept {
        return SharedPtr<T>(*this);
    }

    bool expired() const noexcept {
        return ptr_.expired();
    }

private:
    friend class SharedPtr<T>;

    std::weak_ptr<T> ptr_;
};

template <typename T>
SharedPtr<T>::SharedPtr(const WeakPtr<T>& r) noexcept : ptr_(r.ptr_.lock()) {}

template <typename T, typename... Args>
SharedPtr<T> makeShared(Args&&... args) {
    return SharedPtr<T>(std::make_shared<T>(std::forward<Args>(args)...));
}

template<class T, class U>
SharedPtr<T> static_pointer_cast(const SharedPtr<U>& r) noexcept
{
    auto* p = static_cast<typename SharedPtr<T>::element_type*>(r.get());
    return SharedPtr<T>{r, p};
}

} // namespace djinni