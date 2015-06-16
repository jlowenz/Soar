//
//  TestRunner.hpp
//  Soar-xcode
//
//  Created by Alex Turner on 6/16/15.
//  Copyright © 2015 University of Michigan – Soar Group. All rights reserved.
//

#ifndef TestRunner_cpp
#define TestRunner_cpp

#include "TestCategory.hpp"

#include <functional>
#include <condition_variable>
#include <atomic>
#include <sstream>

class TestRunner
{
	TestCategory* category;
	TestFunction* function;
	
	std::condition_variable* variable;
	
public:
	TestRunner(TestCategory* category, TestFunction* function, std::condition_variable* variable);
	
	void run();
		
	std::atomic<bool> kill;
	std::atomic<bool> ready;
	std::atomic<bool> done;
	
	std::stringstream output;
};

#endif /* TestRunner_cpp */
