#pragma once

#include <memory>

#include "item_list.hpp"
#include "sort_order.hpp"
#include "textbox_listener.hpp"

namespace textsort {

class SortItemsImpl {

public:
    SortItemsImpl(const std::shared_ptr<TextboxListener> & listener);
    void sort(sort_order order, const ItemList & items);

    static ItemList run_sort(const ItemList & items);

private:
    std::shared_ptr<TextboxListener> m_listener;

};

}
