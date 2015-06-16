//
//  testMain.cpp
//  Soar-xcode
//
//  Created by Alex Turner on 6/16/15.
//  Copyright © 2015 University of Michigan – Soar Group. All rights reserved.
//

#include "TestCategory.hpp"
#include "TestRunner.hpp"

#include <functional>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <iostream>

// INCLUDE TEST HEADERS HERE

#include "ExampleTest.hpp"

int main(int argc, char** argv)
{
	const bool ShowTestOutput = true;
	
	std::condition_variable variable;
	std::mutex mutex;
	std::unique_lock<std::mutex> lock(mutex);
	
	std::vector<TestCategory*> tests;
	
	// DEFINE ALL TESTS HERE
	
	TEST_DECLARATION(ExampleTest);
	
	for (TestCategory* category : tests)
	{
		std::cout << "Running " << category->getCategoryName() << ":" << std::endl;
		
		for (TestCategory::TestCategory_test test : category->getTests())
		{
			std::cout << std::get<2>(test) << ": ";
			std::cout.flush();
			
			TestFunction* function = std::get<0>(test);
			uint64_t timeout = std::get<1>(test) - 1000;

			TestRunner* runner = new TestRunner(category, function, &variable);
			
			std::thread (&TestRunner::run, runner).detach();
			
			uint64_t timeElapsed;
			
			runner->ready.store(true);
			
			variable.notify_all();
			while (variable.wait_for(lock, std::chrono::seconds(1)) == std::cv_status::timeout)
			{
				std::cout << '.';
				std::cout.flush();
				timeElapsed += 1000;
				
				if (timeElapsed > timeout)
					break;
			}
			
			if (timeElapsed > timeout)
			{
				std::cout << "Timeout" << std::endl;
				std::cout.flush();
				
				runner->kill.store(true);
			}
			else
			{
				std::cout << "Done" << std::endl;
				std::cout.flush();
			}
			
			std::mutex mutex;
			std::unique_lock<std::mutex> lock(mutex);
			variable.wait(lock, [runner]{ return runner->done == true; });
			
			if (runner->kill)
			{
				std::cout << "Killed" << std::endl;
				std::cout.flush();
			}
			
			if (ShowTestOutput)
			{
				std::cout << std::endl << std::get<2>(test) << " Output:" << std::endl;
				std::cout << runner->output.str() << std::endl;
				std::cout.flush();
			}
		}
	}
}
